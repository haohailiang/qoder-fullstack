package com.ecommerce.order.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController 集成测试：通过 @SpringBootTest + @AutoConfigureMockMvc
 * 验证 HTTP 请求/响应（状态码、返回格式），使用 @MockBean 模拟 Service 层依赖。
 * 注意：GlobalExceptionHandler 未标注 @ResponseStatus，HTTP 状态恒为 200，
 * 真正的错误码在 Result.body.code 中体现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    // ──────────────────── POST /orders ────────────────────

    @Nested
    @DisplayName("POST /orders - 创建订单")
    class CreateOrder {

        @Test
        @DisplayName("正常场景：创建订单成功")
        void shouldCreateOrder() throws Exception {
            // Given
            Order created = buildOrder(1L, "ORD20250101120000ABCDEF", 100L, 10L, OrderStatus.PENDING_PAYMENT);
            when(orderService.createOrder(eq(100L), any(Order.class))).thenReturn(created);

            String body = """
                    {"productId":10,"productName":"测试商品","quantity":2,"totalAmount":199.98,"address":"北京市朝阳区"}
                    """;

            // When & Then
            mockMvc.perform(post("/orders")
                            .header(HEADER_USER_ID, "100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.orderNo").value("ORD20250101120000ABCDEF"));
        }

        @Test
        @DisplayName("异常场景：缺少 X-User-Id 请求头返回 401")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            String body = """
                    {"productId":10,"quantity":2,"totalAmount":199.98,"address":"北京市朝阳区"}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("未授权访问"));
        }

        @Test
        @DisplayName("异常场景：库存不足返回 400 业务码")
        void shouldReturn400WhenStockInsufficient() throws Exception {
            when(orderService.createOrder(eq(100L), any(Order.class)))
                    .thenThrow(new BusinessException(400, "库存不足或商品不存在"));

            String body = """
                    {"productId":10,"quantity":100,"totalAmount":9999.98,"address":"北京市朝阳区"}
                    """;

            mockMvc.perform(post("/orders")
                            .header(HEADER_USER_ID, "100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("库存不足或商品不存在"));
        }
    }

    // ──────────────────── GET /orders/my ────────────────────

    @Nested
    @DisplayName("GET /orders/my - 查询当前用户订单列表")
    class GetMyOrders {

        @Test
        @DisplayName("正常场景：返回当前用户的订单列表")
        void shouldReturnMyOrders() throws Exception {
            Order order = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.PAID);
            when(orderService.getOrdersByUserId(100L)).thenReturn(List.of(order));

            mockMvc.perform(get("/orders/my")
                            .header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].orderNo").value("ORD-1"));
        }

        @Test
        @DisplayName("空数据：无订单时返回空数组")
        void shouldReturnEmptyArray() throws Exception {
            when(orderService.getOrdersByUserId(200L)).thenReturn(List.of());

            mockMvc.perform(get("/orders/my")
                            .header(HEADER_USER_ID, "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("异常场景：缺少 X-User-Id 请求头返回 401")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/orders/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    // ──────────────────── GET /orders/{orderNo} ────────────────────

    @Nested
    @DisplayName("GET /orders/{orderNo} - 按订单号查询")
    class GetOrderByOrderNo {

        @Test
        @DisplayName("正常场景：订单号存在时返回订单详情")
        void shouldReturnOrderByOrderNo() throws Exception {
            Order order = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.SHIPPED);
            when(orderService.getOrderByOrderNo("ORD-1")).thenReturn(order);

            mockMvc.perform(get("/orders/ORD-1")
                            .header(HEADER_USER_ID, "100")
                            .header(HEADER_USER_NAME, "zhangsan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.orderNo").value("ORD-1"))
                    .andExpect(jsonPath("$.data.status").value("SHIPPED"));
        }

        @Test
        @DisplayName("正常场景：ADMIN 可查看他人订单")
        void shouldReturnOtherUsersOrderForAdmin() throws Exception {
            Order order = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.SHIPPED);
            when(orderService.getOrderByOrderNo("ORD-1")).thenReturn(order);

            mockMvc.perform(get("/orders/ORD-1")
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_NAME, "admin")
                            .header(HEADER_USER_ROLE, "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("归属校验：非归属人查看他人订单返回 403")
        void shouldReturn403WhenNotOwner() throws Exception {
            Order order = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.SHIPPED);
            when(orderService.getOrderByOrderNo("ORD-1")).thenReturn(order);

            mockMvc.perform(get("/orders/ORD-1")
                            .header(HEADER_USER_ID, "200")
                            .header(HEADER_USER_NAME, "lisi"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权查看此订单"));
        }

        @Test
        @DisplayName("异常场景：未登录查询订单详情返回 401")
        void shouldReturn401WhenAnonymous() throws Exception {
            Order order = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.SHIPPED);
            when(orderService.getOrderByOrderNo("ORD-1")).thenReturn(order);

            mockMvc.perform(get("/orders/ORD-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("异常场景：订单号不存在返回 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(orderService.getOrderByOrderNo("NONEXIST"))
                    .thenThrow(new BusinessException(404, "订单不存在: NONEXIST"));

            mockMvc.perform(get("/orders/NONEXIST")
                            .header(HEADER_USER_ID, "100")
                            .header(HEADER_USER_NAME, "zhangsan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("订单不存在: NONEXIST"));
        }
    }

    // ──────────────────── GET /orders ────────────────────

    @Nested
    @DisplayName("GET /orders - 查询所有订单（管理用）")
    class GetAllOrders {

        @Test
        @DisplayName("正常场景：返回全部订单列表")
        void shouldReturnAllOrders() throws Exception {
            Order order1 = buildOrder(1L, "ORD-1", 100L, 10L, OrderStatus.PAID);
            Order order2 = buildOrder(2L, "ORD-2", 200L, 20L, OrderStatus.COMPLETED);
            when(orderService.getAllOrders()).thenReturn(List.of(order1, order2));

            mockMvc.perform(get("/orders")
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_NAME, "admin")
                            .header(HEADER_USER_ROLE, "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isNotEmpty());
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 查询全部订单返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(get("/orders")
                            .header(HEADER_USER_ID, "100")
                            .header(HEADER_USER_NAME, "zhangsan")
                            .header(HEADER_USER_ROLE, "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ──────────────────── POST /orders/{orderId}/cancel ────────────────────

    @Nested
    @DisplayName("POST /orders/{orderId}/cancel - 取消订单")
    class CancelOrder {

        @Test
        @DisplayName("正常场景：取消订单成功")
        void shouldCancelOrder() throws Exception {
            doNothing().when(orderService).cancelOrder(1L, 100L);

            mockMvc.perform(post("/orders/1/cancel")
                            .header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：无权操作返回 403")
        void shouldReturn403WhenNotOwner() throws Exception {
            doThrow(new BusinessException(403, "无权操作此订单"))
                    .when(orderService).cancelOrder(1L, 200L);

            mockMvc.perform(post("/orders/1/cancel")
                            .header(HEADER_USER_ID, "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权操作此订单"));
        }

        @Test
        @DisplayName("异常场景：订单状态不可取消返回 400")
        void shouldReturn400WhenStatusNotCancellable() throws Exception {
            doThrow(new BusinessException(400, "当前订单状态不可取消"))
                    .when(orderService).cancelOrder(1L, 100L);

            mockMvc.perform(post("/orders/1/cancel")
                            .header(HEADER_USER_ID, "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("当前订单状态不可取消"));
        }
    }

    // ──────────────────── PUT /orders/{orderId}/status ────────────────────

    @Nested
    @DisplayName("PUT /orders/{orderId}/status - 更新订单状态")
    class UpdateStatus {

        @Test
        @DisplayName("正常场景：更新订单状态为已发货")
        void shouldUpdateStatusToShipped() throws Exception {
            doNothing().when(orderService).updateOrderStatus(1L, OrderStatus.SHIPPED);

            mockMvc.perform(put("/orders/1/status")
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_NAME, "admin")
                            .header(HEADER_USER_ROLE, "ADMIN")
                            .param("status", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("正常场景：更新订单状态为已完成")
        void shouldUpdateStatusToCompleted() throws Exception {
            doNothing().when(orderService).updateOrderStatus(1L, OrderStatus.COMPLETED);

            mockMvc.perform(put("/orders/1/status")
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_NAME, "admin")
                            .header(HEADER_USER_ROLE, "ADMIN")
                            .param("status", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：无效状态值返回 400")
        void shouldReturn400ForInvalidStatus() throws Exception {
            mockMvc.perform(put("/orders/1/status")
                            .header(HEADER_USER_ID, "1")
                            .header(HEADER_USER_NAME, "admin")
                            .header(HEADER_USER_ROLE, "ADMIN")
                            .param("status", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("无效的订单状态值: 99"));
        }
    }

    // ──────────────────── helper ────────────────────

    private Order buildOrder(Long id, String orderNo, Long userId, Long productId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName("测试商品-" + productId);
        order.setQuantity(1);
        order.setTotalAmount(new BigDecimal("99.99"));
        order.setStatus(status);
        order.setAddress("北京市朝阳区");
        return order;
    }
}
