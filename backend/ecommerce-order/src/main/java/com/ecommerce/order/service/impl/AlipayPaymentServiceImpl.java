package com.ecommerce.order.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.ecommerce.common.BusinessException;
import com.ecommerce.order.config.AlipayProperties;
import com.ecommerce.order.dto.PaymentCreateResponse;
import com.ecommerce.order.dto.PaymentStatusResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.PaymentRecord;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.entity.enums.PaymentStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRecordRepository;
import com.ecommerce.order.service.PaymentService;
import com.ecommerce.order.service.PaymentStateManager;
import com.ecommerce.order.service.PaymentStateManager.PaidResult;
import com.ecommerce.order.service.alipay.AlipayClientFactory;
import com.ecommerce.order.service.alipay.AlipayKeyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝沙箱支付实现。
 *
 * <p>安全与幂等约定：
 * <ul>
 *   <li>回调必须验签通过，且 app_id / 订单号 / 金额全部匹配才会流转订单状态；</li>
 *   <li>状态流转统一交给 {@link PaymentStateManager}，条件更新保证重复通知只生效一次；</li>
 *   <li>验签失败、金额不一致、订单不存在等异常一律记录告警日志并返回 failure。</li>
 * </ul>
 */
@Service
public class AlipayPaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentServiceImpl.class);

    private static final String CHANNEL = PaymentStateManager.CHANNEL_ALIPAY;
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";
    private static final String TRADE_CLOSED = "TRADE_CLOSED";
    /** 支付宝要求的回调应答 */
    private static final String NOTIFY_SUCCESS = "success";
    private static final String NOTIFY_FAILURE = "failure";
    private static final int SUBJECT_MAX_LENGTH = 128;

    private static final String PAY_STATUS_PAID = "PAID";
    private static final String PAY_STATUS_PENDING = "PENDING";
    private static final String PAY_STATUS_CLOSED = "CLOSED";
    private static final String PAY_STATUS_CANCELLED = "CANCELLED";
    private static final String PAY_STATUS_FAILED = "FAILED";

    private final AlipayProperties properties;
    private final AlipayClientFactory alipayClientFactory;
    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentStateManager paymentStateManager;
    private final ObjectMapper objectMapper;

    public AlipayPaymentServiceImpl(AlipayProperties properties,
                                    AlipayClientFactory alipayClientFactory,
                                    OrderRepository orderRepository,
                                    PaymentRecordRepository paymentRecordRepository,
                                    PaymentStateManager paymentStateManager,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.alipayClientFactory = alipayClientFactory;
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentStateManager = paymentStateManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logConfiguration() {
        if (properties.isConfigured()) {
            log.info("支付宝沙箱支付已启用: gatewayUrl={}, notifyUrl={}, returnUrl={}",
                    properties.getGatewayUrl(), properties.getNotifyUrl(), properties.getReturnUrl());
        } else {
            log.warn("支付宝支付未配置（ALIPAY_APP_ID / ALIPAY_PRIVATE_KEY / ALIPAY_PUBLIC_KEY 为空），"
                    + "创建支付将返回 503，其余下单路径不受影响");
        }
    }

    @Override
    public PaymentCreateResponse createPayment(Order order) {
        if (!properties.isConfigured()) {
            log.warn("创建支付被拒绝：支付宝支付未配置, orderNo={}", order.getOrderNo());
            throw new BusinessException(503, "支付宝支付未配置，请联系管理员");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(400, "当前订单状态不可支付");
        }

        AlipayClient client = alipayClientFactory.getClient();
        if (client == null) {
            throw new BusinessException(503, "支付宝支付未配置，请联系管理员");
        }

        BigDecimal amount = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizContent(buildBizContent(order, amount));

        if (hasText(properties.getNotifyUrl())) {
            request.setNotifyUrl(properties.getNotifyUrl());
        }
        String returnUrl = buildReturnUrl(order.getOrderNo());
        if (returnUrl != null) {
            request.setReturnUrl(returnUrl);
        }

        String payUrl;
        try {
            payUrl = client.pageExecute(request, "GET").getBody();
        } catch (AlipayApiException e) {
            log.error("支付宝下单失败: orderNo={}, error={}", order.getOrderNo(), e.getMessage());
            throw new BusinessException(502, "支付宝下单失败，请稍后重试");
        }

        recordCreatedPayment(order, amount);
        log.info("创建支付宝支付成功: orderNo={}, amount={}", order.getOrderNo(), amount);
        return new PaymentCreateResponse(order.getOrderNo(), CHANNEL, amount, payUrl, properties.getTimeoutExpress());
    }

    @Override
    public PaymentStatusResponse queryStatus(Order order, boolean sync) {
        if (sync && order.getStatus() == OrderStatus.PENDING_PAYMENT && properties.isConfigured()) {
            syncFromAlipay(order.getOrderNo());
        }
        return buildStatusResponse(order.getOrderNo());
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        try {
            if (!properties.isConfigured()) {
                log.error("[ALIPAY-ALARM] 收到支付宝回调但支付参数未配置: outTradeNo={}", params.get("out_trade_no"));
                return NOTIFY_FAILURE;
            }

            if (!verifySignature(params)) {
                log.error("[ALIPAY-ALARM] 支付宝回调验签失败: outTradeNo={}, tradeNo={}",
                        params.get("out_trade_no"), params.get("trade_no"));
                return NOTIFY_FAILURE;
            }

            if (!properties.getAppId().equals(params.get("app_id"))) {
                log.error("[ALIPAY-ALARM] 支付宝回调 app_id 不匹配: expected={}, actual={}",
                        properties.getAppId(), params.get("app_id"));
                return NOTIFY_FAILURE;
            }

            String orderNo = params.get("out_trade_no");
            if (!hasText(orderNo)) {
                log.error("[ALIPAY-ALARM] 支付宝回调缺少 out_trade_no: params={}", params);
                return NOTIFY_FAILURE;
            }

            String tradeStatus = params.get("trade_status");
            if (!TRADE_SUCCESS.equals(tradeStatus) && !TRADE_FINISHED.equals(tradeStatus)) {
                // 非成功终态（如 WAIT_BUYER_PAY）仅确认收到，不改变订单状态
                log.info("支付宝回调非成功终态，忽略: orderNo={}, tradeStatus={}", orderNo, tradeStatus);
                return NOTIFY_SUCCESS;
            }

            BigDecimal paidAmount = parseAmount(params.get("total_amount"), orderNo);
            if (paidAmount == null) {
                return NOTIFY_FAILURE;
            }

            PaidResult result = paymentStateManager.markPaid(
                    orderNo, params.get("trade_no"), paidAmount, toPayload(params));
            return result == PaidResult.CONFLICT ? NOTIFY_FAILURE : NOTIFY_SUCCESS;
        } catch (Exception e) {
            // 回调接口必须始终返回文本应答，避免全局异常处理返回 JSON 导致支付宝持续重试
            log.error("[ALIPAY-ALARM] 处理支付宝回调异常", e);
            return NOTIFY_FAILURE;
        }
    }

    /** 校验支付宝回调签名 */
    private boolean verifySignature(Map<String, String> params) throws AlipayApiException {
        String signType = hasText(params.get("sign_type")) ? params.get("sign_type") : properties.getSignType();
        return AlipaySignature.rsaCheckV1(
                params,
                AlipayKeyUtils.normalize(properties.getAlipayPublicKey()),
                properties.getCharset(),
                signType);
    }

    /** 主动查询支付宝交易状态并同步本地订单 */
    private void syncFromAlipay(String orderNo) {
        AlipayClient client = alipayClientFactory.getClient();
        if (client == null) {
            return;
        }
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizContent(objectMapper.writeValueAsString(Map.of("out_trade_no", orderNo)));
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                log.warn("支付宝订单查询未成功: orderNo={}, code={}, msg={}, subMsg={}",
                        orderNo, response.getCode(), response.getMsg(), response.getSubMsg());
                return;
            }

            String tradeStatus = response.getTradeStatus();
            if (TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus)) {
                BigDecimal paidAmount = response.getTotalAmount() == null
                        ? null : new BigDecimal(response.getTotalAmount());
                paymentStateManager.markPaid(orderNo, response.getTradeNo(), paidAmount, null);
            } else if (TRADE_CLOSED.equals(tradeStatus)) {
                paymentStateManager.markClosed(orderNo);
            }
        } catch (JsonProcessingException e) {
            log.error("构造支付宝查询请求失败: orderNo={}, error={}", orderNo, e.getMessage());
        } catch (Exception e) {
            // 查询失败不阻断返回页展示，前端可继续轮询或稍后重试
            log.warn("支付宝订单查询失败: orderNo={}, error={}", orderNo, e.getMessage());
        }
    }

    private PaymentStatusResponse buildStatusResponse(String orderNo) {
        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setOrderNo(orderNo);

        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            response.setPayStatus(PAY_STATUS_FAILED);
            response.setMessage("订单不存在");
            return response;
        }

        PaymentRecord record = paymentRecordRepository.findByOrderNo(CHANNEL, orderNo);
        response.setOrderStatus(order.getStatus().getCode());
        response.setAmount(order.getTotalAmount());
        if (record != null) {
            response.setTradeNo(record.getTradeNo());
            response.setPaidTime(record.getPaidTime());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            response.setPayStatus(PAY_STATUS_CANCELLED);
            response.setMessage("订单已取消");
        } else if (PaymentStateManager.isPaidStatus(order.getStatus())) {
            response.setPayStatus(PAY_STATUS_PAID);
            response.setMessage("支付成功");
        } else if (PaymentStateManager.isClosed(record)) {
            response.setPayStatus(PAY_STATUS_CLOSED);
            response.setMessage("交易已关闭或超时未支付");
        } else {
            response.setPayStatus(PAY_STATUS_PENDING);
            response.setMessage("支付结果确认中");
        }
        return response;
    }

    /** 落支付流水：同一订单重复创建支付时唯一键冲突被 INSERT IGNORE 静默忽略 */
    private void recordCreatedPayment(Order order, BigDecimal amount) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo(order.getOrderNo());
        record.setChannel(CHANNEL);
        record.setAmount(amount);
        record.setStatus(PaymentStatus.CREATED);
        try {
            paymentRecordRepository.insertIgnore(record);
        } catch (Exception e) {
            // 流水写入失败不应阻断支付跳转
            log.warn("支付流水写入失败: orderNo={}, error={}", order.getOrderNo(), e.getMessage());
        }
    }

    private String buildBizContent(Order order, BigDecimal amount) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("product_code", PRODUCT_CODE);
        bizContent.put("total_amount", amount.toPlainString());
        bizContent.put("subject", truncateSubject(order.getProductName()));
        bizContent.put("timeout_express", properties.getTimeoutExpress());
        try {
            return objectMapper.writeValueAsString(bizContent);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "构造支付宝支付参数失败");
        }
    }

    /** return_url 追加 orderNo，便于结果页在支付宝未回传 out_trade_no 时兜底 */
    private String buildReturnUrl(String orderNo) {
        if (!hasText(properties.getReturnUrl())) {
            return null;
        }
        String separator = properties.getReturnUrl().contains("?") ? "&" : "?";
        return properties.getReturnUrl() + separator + "orderNo="
                + URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
    }

    private BigDecimal parseAmount(String totalAmount, String orderNo) {
        if (!hasText(totalAmount)) {
            log.error("[ALIPAY-ALARM] 支付宝回调缺少 total_amount: orderNo={}", orderNo);
            return null;
        }
        try {
            return new BigDecimal(totalAmount);
        } catch (NumberFormatException e) {
            log.error("[ALIPAY-ALARM] 支付宝回调金额格式非法: orderNo={}, totalAmount={}", orderNo, totalAmount);
            return null;
        }
    }

    private String toPayload(Map<String, String> params) {
        try {
            return PaymentStateManager.truncate(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String truncateSubject(String subject) {
        String value = hasText(subject) ? subject : "商品订单";
        return value.length() <= SUBJECT_MAX_LENGTH ? value : value.substring(0, SUBJECT_MAX_LENGTH);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}