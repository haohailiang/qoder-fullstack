package com.ecommerce.order.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
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
import com.ecommerce.order.service.PaymentStateManager.PaidResult;
import com.ecommerce.order.service.impl.AlipayPaymentServiceImpl;
import com.ecommerce.order.service.alipay.AlipayClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AlipayPaymentServiceImpl 单元测试。
 *
 * <p>重点覆盖三条安全边界：
 * <ol>
 *   <li>未配置支付宝参数时创建支付返回 503，不影响其他下单路径；</li>
 *   <li>回调验签失败 / app_id 不匹配 / 金额非法 / 订单号缺失一律返回 failure 且不改订单；</li>
 *   <li>回调幂等：重复成功通知返回 success 但只允许一次状态流转。</li>
 * </ol>
 * 验签使用真实 RSA 密钥对（见 {@link com.ecommerce.order.service.alipay.AlipaySignatureTest}），不打桩。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlipayPaymentServiceImpl - 支付宝支付服务")
class AlipayPaymentServiceImplTest {

    private static final String APP_ID = "2021000000000000";
    private static final String ORDER_NO = "ORD20250101120000ABCDEF";
    private static final String TRADE_NO = "2025010122001440000000000001";
    private static final String PAY_URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do?biz_content=%7B%7D";
    private static final BigDecimal AMOUNT = new BigDecimal("199.98");

    private static String privateKeyBase64;
    private static String publicKeyBase64;

    @Mock
    private AlipayClientFactory alipayClientFactory;

    @Mock
    private AlipayClient alipayClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private PaymentStateManager paymentStateManager;

    private AlipayProperties properties;

    private AlipayPaymentServiceImpl paymentService;

    private Order pendingOrder;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @BeforeEach
    void setUp() {
        properties = new AlipayProperties();
        properties.setAppId(APP_ID);
        properties.setPrivateKey(privateKeyBase64);
        properties.setAlipayPublicKey(publicKeyBase64);
        properties.setReturnUrl("http://localhost:5173/order/pay-result");

        paymentService = new AlipayPaymentServiceImpl(
                properties, alipayClientFactory, orderRepository, paymentRecordRepository,
                paymentStateManager, new ObjectMapper());

        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setOrderNo(ORDER_NO);
        pendingOrder.setUserId(100L);
        pendingOrder.setProductName("测试商品");
        pendingOrder.setTotalAmount(AMOUNT);
        pendingOrder.setStatus(OrderStatus.PENDING_PAYMENT);
    }

    /** 构造并签名一份支付宝异步通知参数 */
    private static Map<String, String> signedNotifyParams() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", APP_ID);
        params.put("out_trade_no", ORDER_NO);
        params.put("trade_no", TRADE_NO);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "199.98");
        params.put("subject", "测试商品");
        sign(params);
        return params;
    }

    /**
     * 按支付宝规则签名：签名原文由 SDK 的 {@code getSignContent} 生成，
     * 与 {@code rsaCheckV1} 使用的待验签原文一致。
     */
    private static void sign(Map<String, String> params) throws Exception {
        params.remove("sign");
        String content = AlipaySignature.getSignContent(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKeyBase64, "UTF-8", "RSA2"));
    }

    // ──────────────────── createPayment ────────────────────

    @Nested
    @DisplayName("createPayment - 创建支付")
    class CreatePayment {

        @Test
        @DisplayName("未配置支付宝参数：抛出 503 业务异常")
        void shouldThrow503WhenNotConfigured() {
            properties.setAppId("");

            assertThatThrownBy(() -> paymentService.createPayment(pendingOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("支付宝支付未配置，请联系管理员");
            verifyNoInteractions(alipayClientFactory);
        }

        @Test
        @DisplayName("订单非待支付：抛出 400 业务异常")
        void shouldThrow400WhenStatusNotPending() {
            pendingOrder.setStatus(OrderStatus.PAID);

            assertThatThrownBy(() -> paymentService.createPayment(pendingOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("当前订单状态不可支付");
        }

        @Test
        @DisplayName("正常场景：返回收银台地址并落一条支付流水")
        void shouldReturnPayUrlAndRecordPayment() throws Exception {
            AlipayTradePagePayResponse pageResponse = new AlipayTradePagePayResponse();
            pageResponse.setBody(PAY_URL);
            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradePagePayResponse>pageExecute(
                    any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(pageResponse);

            PaymentCreateResponse response = paymentService.createPayment(pendingOrder);

            assertThat(response.getPayUrl()).isEqualTo(PAY_URL);
            assertThat(response.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(response.getChannel()).isEqualTo("ALIPAY");
            assertThat(response.getAmount()).isEqualByComparingTo(AMOUNT);

            ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
            verify(paymentRecordRepository).insertIgnore(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.CREATED);
            assertThat(captor.getValue().getChannel()).isEqualTo("ALIPAY");
            assertThat(captor.getValue().getOrderNo()).isEqualTo(ORDER_NO);
        }

        @Test
        @DisplayName("调用支付宝失败：抛出 502 业务异常且不落流水")
        void shouldThrow502WhenAlipayCallFails() throws Exception {
            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradePagePayResponse>pageExecute(
                    any(AlipayTradePagePayRequest.class), eq("GET")))
                    .thenThrow(new com.alipay.api.AlipayApiException("network error"));

            assertThatThrownBy(() -> paymentService.createPayment(pendingOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("支付宝下单失败，请稍后重试");
            verifyNoInteractions(paymentRecordRepository);
        }

        @Test
        @DisplayName("流水写入异常：不阻断支付跳转")
        void shouldNotFailWhenRecordInsertThrows() throws Exception {
            AlipayTradePagePayResponse pageResponse = new AlipayTradePagePayResponse();
            pageResponse.setBody(PAY_URL);
            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradePagePayResponse>pageExecute(
                    any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(pageResponse);
            when(paymentRecordRepository.insertIgnore(any(PaymentRecord.class)))
                    .thenThrow(new RuntimeException("db down"));

            assertThat(paymentService.createPayment(pendingOrder).getPayUrl()).isEqualTo(PAY_URL);
        }
    }

    // ──────────────────── handleNotify ────────────────────

    @Nested
    @DisplayName("handleNotify - 异步通知")
    class HandleNotify {

        @Test
        @DisplayName("未配置支付参数：返回 failure 且不改订单")
        void shouldReturnFailureWhenNotConfigured() {
            properties.setAppId("");

            assertThat(paymentService.handleNotify(Map.of())).isEqualTo("failure");
            verifyNoInteractions(paymentStateManager);
        }

        @Test
        @DisplayName("验签失败：返回 failure 且不改订单")
        void shouldReturnFailureWhenSignatureInvalid() throws Exception {
            Map<String, String> params = signedNotifyParams();
            params.put("sign", "invalid-signature");

            assertThat(paymentService.handleNotify(params)).isEqualTo("failure");
            verifyNoInteractions(paymentStateManager);
        }

        @Test
        @DisplayName("app_id 不匹配：返回 failure 且不改订单")
        void shouldReturnFailureWhenAppIdMismatch() throws Exception {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", "2021999999999999");
            params.put("out_trade_no", ORDER_NO);
            params.put("trade_no", TRADE_NO);
            params.put("trade_status", "TRADE_SUCCESS");
            params.put("total_amount", "199.98");
            sign(params);

            assertThat(paymentService.handleNotify(params)).isEqualTo("failure");
            verifyNoInteractions(paymentStateManager);
        }

        @Test
        @DisplayName("缺少 out_trade_no：返回 failure")
        void shouldReturnFailureWhenOrderNoMissing() throws Exception {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", APP_ID);
            params.put("trade_status", "TRADE_SUCCESS");
            params.put("total_amount", "199.98");
            sign(params);

            assertThat(paymentService.handleNotify(params)).isEqualTo("failure");
            verifyNoInteractions(paymentStateManager);
        }

        @Test
        @DisplayName("金额格式非法：返回 failure 且不改订单")
        void shouldReturnFailureWhenAmountInvalid() throws Exception {
            Map<String, String> params = signedNotifyParams();
            params.put("total_amount", "not-a-number");

            assertThat(paymentService.handleNotify(params)).isEqualTo("failure");
            verifyNoInteractions(paymentStateManager);
        }

        @Test
        @DisplayName("非成功终态（WAIT_BUYER_PAY）：确认收到但不改状态")
        void shouldAckWithoutStateChangeForWaitBuyerPay() throws Exception {
            Map<String, String> params = signedNotifyParams();
            params.put("trade_status", "WAIT_BUYER_PAY");
            // trade_status 参与签名，需重新签名
            sign(params);

            assertThat(paymentService.handleNotify(params)).isEqualTo("success");
            verify(paymentStateManager, never())
                    .markPaid(any(), any(), any(), any());
        }

        @Test
        @DisplayName("支付成功：落库并返回 success")
        void shouldMarkPaidAndReturnSuccess() throws Exception {
            when(paymentStateManager.markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any()))
                    .thenReturn(PaidResult.APPLIED);

            assertThat(paymentService.handleNotify(signedNotifyParams())).isEqualTo("success");
            verify(paymentStateManager).markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("重复通知（订单已支付）：仍返回 success，不产生二次状态变更")
        void shouldReturnSuccessForDuplicateNotify() throws Exception {
            when(paymentStateManager.markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any()))
                    .thenReturn(PaidResult.ALREADY_PAID);

            assertThat(paymentService.handleNotify(signedNotifyParams())).isEqualTo("success");
            verify(paymentStateManager).markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("状态冲突（订单已取消）：返回 failure")
        void shouldReturnFailureOnConflict() throws Exception {
            when(paymentStateManager.markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any()))
                    .thenReturn(PaidResult.CONFLICT);

            assertThat(paymentService.handleNotify(signedNotifyParams())).isEqualTo("failure");
        }
    }

    // ──────────────────── queryStatus ────────────────────

    @Nested
    @DisplayName("queryStatus - 主动查询")
    class QueryStatus {

        @Test
        @DisplayName("已支付订单：直接返回 PAID，不再请求支付宝")
        void shouldReturnPaidWithoutCallingAlipay() {
            Order paidOrder = buildOrder(OrderStatus.PAID);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(paidOrder);

            PaymentStatusResponse response = paymentService.queryStatus(paidOrder, true);

            assertThat(response.getPayStatus()).isEqualTo("PAID");
            assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID.getCode());
            verifyNoInteractions(alipayClientFactory);
        }

        @Test
        @DisplayName("已取消订单：返回 CANCELLED")
        void shouldReturnCancelled() {
            Order cancelled = buildOrder(OrderStatus.CANCELLED);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(cancelled);

            assertThat(paymentService.queryStatus(cancelled, true).getPayStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("待支付且流水已关闭：返回 CLOSED")
        void shouldReturnClosedWhenRecordClosed() {
            PaymentRecord record = new PaymentRecord();
            record.setStatus(PaymentStatus.CLOSED);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);
            when(paymentRecordRepository.findByOrderNo("ALIPAY", ORDER_NO)).thenReturn(record);

            PaymentStatusResponse response = paymentService.queryStatus(pendingOrder, false);

            assertThat(response.getPayStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("待支付：sync=true 时向支付宝查询并同步为已支付")
        void shouldSyncPaidFromAlipay() throws Exception {
            Order paidOrder = buildOrder(OrderStatus.PAID);
            AlipayTradeQueryResponse queryResponse = new AlipayTradeQueryResponse();
            queryResponse.setCode("10000");
            queryResponse.setTradeStatus("TRADE_SUCCESS");
            queryResponse.setTradeNo(TRADE_NO);
            queryResponse.setTotalAmount("199.98");

            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradeQueryResponse>execute(any(AlipayTradeQueryRequest.class)))
                    .thenReturn(queryResponse);
            // 同步后重新读取订单（真实环境已由条件更新为已支付）
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(paidOrder);
            when(paymentStateManager.markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any()))
                    .thenReturn(PaidResult.APPLIED);

            PaymentStatusResponse response = paymentService.queryStatus(pendingOrder, true);

            verify(paymentStateManager).markPaid(eq(ORDER_NO), eq(TRADE_NO), any(BigDecimal.class), any());
            assertThat(response.getPayStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("待支付：支付宝返回 TRADE_CLOSED 时关闭流水，订单仍待支付")
        void shouldMarkClosedWhenAlipayTradeClosed() throws Exception {
            AlipayTradeQueryResponse queryResponse = new AlipayTradeQueryResponse();
            queryResponse.setCode("10000");
            queryResponse.setTradeStatus("TRADE_CLOSED");

            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradeQueryResponse>execute(any(AlipayTradeQueryRequest.class)))
                    .thenReturn(queryResponse);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);

            PaymentStatusResponse response = paymentService.queryStatus(pendingOrder, true);

            verify(paymentStateManager).markClosed(ORDER_NO);
            verify(paymentStateManager, never()).markPaid(any(), any(), any(), any());
            assertThat(response.getPayStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("待支付：支付宝查询异常时不影响结果页展示，返回 PENDING")
        void shouldReturnPendingWhenAlipayQueryFails() throws Exception {
            when(alipayClientFactory.getClient()).thenReturn(alipayClient);
            when(alipayClient.<AlipayTradeQueryResponse>execute(any(AlipayTradeQueryRequest.class)))
                    .thenThrow(new com.alipay.api.AlipayApiException("timeout"));
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);

            assertThat(paymentService.queryStatus(pendingOrder, true).getPayStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("订单不存在：返回 FAILED 并提示")
        void shouldReturnFailedWhenOrderMissing() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(null);

            PaymentStatusResponse response = paymentService.queryStatus(pendingOrder, false);

            assertThat(response.getPayStatus()).isEqualTo("FAILED");
            assertThat(response.getMessage()).isEqualTo("订单不存在");
        }
    }

    private static Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo(ORDER_NO);
        order.setUserId(100L);
        order.setProductName("测试商品");
        order.setTotalAmount(AMOUNT);
        order.setStatus(status);
        return order;
    }
}