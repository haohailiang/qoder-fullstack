package com.ecommerce.order.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.common.security.TrustedAuthenticationFilter;
import com.ecommerce.order.dto.PaymentCreateResponse;
import com.ecommerce.order.dto.PaymentStatusResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付接口：创建支付、异步通知、主动查询。
 *
 * <p>创建支付与查询需要登录态（网关注入的 X-User-Id），并校验订单归属；
 * 异步通知由支付宝服务器直接调用，仅依赖验签，返回纯文本 success/failure。
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "支付管理", description = "支付宝沙箱支付：创建支付 / 异步通知 / 主动查询")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final OrderService orderService;

    public PaymentController(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    /** 创建支付宝支付：返回收银台跳转地址 */
    @PostMapping("/alipay/{orderNo}")
    @Operation(summary = "创建支付宝支付",
            description = "根据订单号生成支付宝收银台地址；未配置支付宝参数时返回业务码 503")
    public Result<PaymentCreateResponse> createAlipayPayment(
            @Parameter(description = "订单号") @PathVariable String orderNo,
            HttpServletRequest request) {
        Order order = orderService.getOrderByOrderNo(orderNo);
        assertOrderAccess(order, request);
        return Result.success(paymentService.createPayment(order));
    }

    /** 查询支付状态：sync=true 时先向支付宝主动查询并同步本地状态 */
    @GetMapping("/alipay/{orderNo}/status")
    @Operation(summary = "查询支付状态",
            description = "供支付结果页轮询使用；sync=true 时主动向支付宝查询并同步订单状态")
    public Result<PaymentStatusResponse> getPaymentStatus(
            @Parameter(description = "订单号") @PathVariable String orderNo,
            @Parameter(description = "是否主动向支付宝查询") @RequestParam(defaultValue = "true") boolean sync,
            HttpServletRequest request) {
        Order order = orderService.getOrderByOrderNo(orderNo);
        assertOrderAccess(order, request);
        return Result.success(paymentService.queryStatus(order, sync));
    }

    /**
     * 支付宝异步通知。
     *
     * <p>支付宝要求应答体为纯文本 success/failure（不带引号、非 JSON），
     * 因此直接返回字符串，并且内部吞掉所有异常，避免全局异常处理返回 Result。
     */
    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "支付宝异步通知",
            description = "接收支付宝支付结果通知，完成验签、金额与订单校验、幂等处理后更新订单状态")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        log.info("收到支付宝异步通知: outTradeNo={}, tradeStatus={}",
                params.get("out_trade_no"), params.get("trade_status"));
        return paymentService.handleNotify(params);
    }

    /** 归属校验：普通用户只能操作自己的订单，ADMIN 放行 */
    private void assertOrderAccess(Order order, HttpServletRequest request) {
        if (isAdmin(request)) {
            return;
        }
        Long userId = getUserIdFromHeader(request);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
    }

    private Long getUserIdFromHeader(HttpServletRequest request) {
        String userIdStr = request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ID);
        if (userIdStr == null) {
            throw new BusinessException(401, "未授权访问");
        }
        return Long.parseLong(userIdStr);
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equalsIgnoreCase(request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ROLE));
    }
}