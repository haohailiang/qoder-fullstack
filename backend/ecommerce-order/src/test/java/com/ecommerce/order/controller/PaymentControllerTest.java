package com.ecommerce.order.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.order.dto.PaymentCreateResponse;
import com.ecommerce.order.dto.PaymentStatusResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentController 集成测试：验证支付接口的鉴权头、归属校验与回调纯文本应答。
 *
 * <p>与 {@link OrderControllerTest} 一致，HTTP 状态恒为 200，业务错误体现在 {@code Result.code}；
 * 支付宝异步通知是例外，必须返回纯文本 success/failure。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PaymentController - 支付接口")
class PaymentControllerTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ORDER_NO = "ORD20250101120000ABCDEF";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private PaymentService paymentService;

    private static Order buildOrder(Long userId) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo(ORDER_NO);
        order.setUserId(userId);
        order.setProductName("测试商品");
        order.setTotalAmount(new BigDecimal("199.98"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return order;
    }

    // ──────────────────── POST /payments/alipay/{orderNo} ────────────────────

    @Nested
    @DisplayName("POST /payments/alipay/{orderNo} - 创建支付")
    class CreatePayment {

        @Test
        @DisplayName("正常场景：返回收银台地址")
        void shouldReturnPayUrl() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(100L));
            when(paymentService.createPayment(any(Order.class))).thenReturn(
                    new PaymentCreateResponse(ORDER_NO, "ALIPAY", new BigDecimal("199.98"),
                            "https://openapi-sandbox.dl.alipaydev.com/gateway.do?sign=xxx", "30m"));

            mockMvc.perform(post("/payments/alipay/{orderNo}", ORDER_NO).header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.orderNo").value(ORDER_NO))
                    .andExpect(jsonPath("$.data.payUrl").value(
                            "https://openapi-sandbox.dl.alipaydev.com/gateway.do?sign=xxx"));
        }

        @Test
        @DisplayName("未配置支付宝：返回业务码 503")
        void shouldReturn503WhenNotConfigured() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(100L));
            when(paymentService.createPayment(any(Order.class)))
                    .thenThrow(new BusinessException(503, "支付宝支付未配置，请联系管理员"));

            mockMvc.perform(post("/payments/alipay/{orderNo}", ORDER_NO).header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(503));
        }

        @Test
        @DisplayName("缺少 X-User-Id：返回 401 且不调用支付服务")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(100L));

            mockMvc.perform(post("/payments/alipay/{orderNo}", ORDER_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));

            verify(paymentService, never()).createPayment(any(Order.class));
        }

        @Test
        @DisplayName("他人订单：返回 403 且不调用支付服务")
        void shouldReturn403WhenOrderBelongsToOther() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(200L));

            mockMvc.perform(post("/payments/alipay/{orderNo}", ORDER_NO).header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));

            verify(paymentService, never()).createPayment(any(Order.class));
        }

        @Test
        @DisplayName("ADMIN 角色：可操作任意订单")
        void shouldAllowAdmin() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(200L));
            when(paymentService.createPayment(any(Order.class))).thenReturn(
                    new PaymentCreateResponse(ORDER_NO, "ALIPAY", new BigDecimal("199.98"), "https://pay", "30m"));

            mockMvc.perform(post("/payments/alipay/{orderNo}", ORDER_NO)
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_ROLE, "ADMIN"))
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ──────────────────── GET /payments/alipay/{orderNo}/status ────────────────────

    @Nested
    @DisplayName("GET /payments/alipay/{orderNo}/status - 查询支付状态")
    class QueryStatus {

        @Test
        @DisplayName("正常场景：返回支付状态")
        void shouldReturnPayStatus() throws Exception {
            PaymentStatusResponse payload = new PaymentStatusResponse();
            payload.setOrderNo(ORDER_NO);
            payload.setOrderStatus(OrderStatus.PAID.getCode());
            payload.setPayStatus("PAID");
            payload.setTradeNo("2025010122001440000000000001");

            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(100L));
            when(paymentService.queryStatus(any(Order.class), eq(true))).thenReturn(payload);

            mockMvc.perform(get("/payments/alipay/{orderNo}/status", ORDER_NO)
                            .header(HEADER_USER_ID, "100")
                            .param("sync", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.payStatus").value("PAID"))
                    .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.PAID.getCode()));
        }

        @Test
        @DisplayName("sync 缺省：默认 true，会触发主动查询")
        void shouldDefaultSyncTrue() throws Exception {
            PaymentStatusResponse payload = new PaymentStatusResponse();
            payload.setOrderNo(ORDER_NO);
            payload.setPayStatus("PENDING");

            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(100L));
            when(paymentService.queryStatus(any(Order.class), anyBoolean())).thenReturn(payload);

            mockMvc.perform(get("/payments/alipay/{orderNo}/status", ORDER_NO).header(HEADER_USER_ID, "100"))
                    .andExpect(jsonPath("$.data.payStatus").value("PENDING"));

            verify(paymentService).queryStatus(any(Order.class), eq(true));
        }

        @Test
        @DisplayName("他人订单：返回 403")
        void shouldReturn403WhenOrderBelongsToOther() throws Exception {
            when(orderService.getOrderByOrderNo(ORDER_NO)).thenReturn(buildOrder(200L));

            mockMvc.perform(get("/payments/alipay/{orderNo}/status", ORDER_NO).header(HEADER_USER_ID, "100"))
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ──────────────────── POST /payments/alipay/notify ────────────────────

    @Nested
    @DisplayName("POST /payments/alipay/notify - 异步通知")
    class Notify {

        @Test
        @DisplayName("处理成功：返回纯文本 success")
        void shouldReturnPlainTextSuccess() throws Exception {
            when(paymentService.handleNotify(anyMap())).thenReturn("success");

            mockMvc.perform(post("/payments/alipay/notify")
                            .param("out_trade_no", ORDER_NO)
                            .param("trade_status", "TRADE_SUCCESS"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("success"));
        }

        @Test
        @DisplayName("验签失败：返回纯文本 failure")
        void shouldReturnPlainTextFailure() throws Exception {
            when(paymentService.handleNotify(anyMap())).thenReturn("failure");

            mockMvc.perform(post("/payments/alipay/notify")
                            .param("out_trade_no", ORDER_NO)
                            .param("sign", "bad"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("failure"));
        }

        @Test
        @DisplayName("无需用户头：支付宝服务器直接回调也能处理")
        void shouldNotRequireUserHeader() throws Exception {
            when(paymentService.handleNotify(anyMap())).thenReturn("success");

            mockMvc.perform(post("/payments/alipay/notify").param("out_trade_no", ORDER_NO))
                    .andExpect(status().isOk())
                    .andExpect(content().string("success"));

            verify(paymentService).handleNotify(Map.of("out_trade_no", ORDER_NO));
        }
    }
}