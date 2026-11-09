package com.ecommerce.order.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 单元测试：覆盖订单创建（含库存扣减）、查询、取消、
 * 状态更新等核心业务逻辑，以及权限校验与异常边界场景。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setId(1L);
        sampleOrder.setOrderNo("ORD20250101120000ABCDEF");
        sampleOrder.setUserId(100L);
        sampleOrder.setProductId(10L);
        sampleOrder.setProductName("测试商品");
        sampleOrder.setQuantity(2);
        sampleOrder.setTotalAmount(new BigDecimal("199.98"));
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        sampleOrder.setAddress("北京市朝阳区");
    }

    // ──────────────────── createOrder ────────────────────

    @Nested
    @DisplayName("createOrder - 创建订单")
    class CreateOrder {

        @Test
        @DisplayName("正常场景：库存扣减成功，创建订单并返回")
        void shouldCreateOrderWhenStockDeducted() {
            // Given
            Order newOrder = new Order();
            newOrder.setProductId(10L);
            newOrder.setQuantity(2);
            newOrder.setTotalAmount(new BigDecimal("199.98"));
            newOrder.setAddress("北京市朝阳区");

            Result<Boolean> deductResult = Result.success(true);
            when(productClient.deductStock(eq(10L), eq(2))).thenReturn(deductResult);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            Order result = orderService.createOrder(100L, newOrder);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(result.getOrderNo()).startsWith("ORD");
            assertThat(result.getCreatedDate()).isNotNull();

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getOrderNo()).isNotNull();
        }

        @Test
        @DisplayName("异常场景：库存不足（deductStock 返回 false）抛出 400 业务异常")
        void shouldThrowWhenStockInsufficient() {
            // Given
            Order newOrder = new Order();
            newOrder.setProductId(10L);
            newOrder.setQuantity(100);

            Result<Boolean> deductResult = Result.success(false);
            when(productClient.deductStock(eq(10L), eq(100))).thenReturn(deductResult);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, newOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 400)
                    .hasMessage("库存不足或商品不存在");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：商品服务调用失败（网络异常）抛出 500 业务异常")
        void shouldThrowWhenProductServiceUnavailable() {
            // Given
            Order newOrder = new Order();
            newOrder.setProductId(10L);
            newOrder.setQuantity(1);

            when(productClient.deductStock(anyLong(), any(Integer.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, newOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 500)
                    .hasMessage("库存扣减失败，请稍后重试");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：商品服务返回 null 视为库存扣减失败")
        void shouldThrowWhenProductClientReturnsNull() {
            // Given
            Order newOrder = new Order();
            newOrder.setProductId(10L);
            newOrder.setQuantity(1);

            when(productClient.deductStock(eq(10L), eq(1))).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, newOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 400)
                    .hasMessage("库存不足或商品不存在");
        }

        @Test
        @DisplayName("异常场景：商品服务返回 Result 但 data 为 null 视为扣减失败")
        void shouldThrowWhenResultDataIsNull() {
            // Given
            Order newOrder = new Order();
            newOrder.setProductId(10L);
            newOrder.setQuantity(1);

            Result<Boolean> deductResult = Result.success(null);
            when(productClient.deductStock(eq(10L), eq(1))).thenReturn(deductResult);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, newOrder))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 400);
        }
    }

    // ──────────────────── getOrdersByUserId ────────────────────

    @Nested
    @DisplayName("getOrdersByUserId - 查询用户订单列表")
    class GetOrdersByUserId {

        @Test
        @DisplayName("正常场景：返回用户的全部订单")
        void shouldReturnOrdersForUser() {
            // Given
            Order order1 = new Order();
            order1.setId(1L);
            order1.setUserId(100L);
            Order order2 = new Order();
            order2.setId(2L);
            order2.setUserId(100L);

            when(orderRepository.findByUserId(100L)).thenReturn(List.of(order1, order2));

            // When
            List<Order> result = orderService.getOrdersByUserId(100L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Order::getUserId).containsOnly(100L);
        }

        @Test
        @DisplayName("空数据：用户无订单时返回空列表")
        void shouldReturnEmptyListForNewUser() {
            when(orderRepository.findByUserId(200L)).thenReturn(Collections.emptyList());

            List<Order> result = orderService.getOrdersByUserId(200L);

            assertThat(result).isEmpty();
        }
    }

    // ──────────────────── getOrderByOrderNo ────────────────────

    @Nested
    @DisplayName("getOrderByOrderNo - 按订单号查询")
    class GetOrderByOrderNo {

        @Test
        @DisplayName("正常场景：订单号存在时返回订单详情")
        void shouldReturnOrderByOrderNo() {
            when(orderRepository.findByOrderNo("ORD20250101120000ABCDEF"))
                    .thenReturn(sampleOrder);

            Order result = orderService.getOrderByOrderNo("ORD20250101120000ABCDEF");

            assertThat(result.getOrderNo()).isEqualTo("ORD20250101120000ABCDEF");
            assertThat(result.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("异常场景：订单号不存在抛出 404 业务异常")
        void shouldThrowWhenOrderNoNotFound() {
            when(orderRepository.findByOrderNo("NONEXIST")).thenReturn(null);

            assertThatThrownBy(() -> orderService.getOrderByOrderNo("NONEXIST"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("订单不存在: NONEXIST");
        }
    }

    // ──────────────────── getOrderById ────────────────────

    @Nested
    @DisplayName("getOrderById - 按 ID 查询订单详情")
    class GetOrderById {

        @Test
        @DisplayName("正常场景：ID 存在时返回订单")
        void shouldReturnOrderById() {
            when(orderRepository.findById(1L)).thenReturn(sampleOrder);

            Order result = orderService.getOrderById(1L);

            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("异常场景：ID 不存在抛出 404 业务异常")
        void shouldThrowWhenIdNotFound() {
            when(orderRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("订单不存在: 999");
        }
    }

    // ──────────────────── cancelOrder ────────────────────

    @Nested
    @DisplayName("cancelOrder - 取消订单")
    class CancelOrder {

        @Test
        @DisplayName("正常场景：订单归属当前用户且状态为待支付时，成功取消")
        void shouldCancelOrderWhenPendingAndOwned() {
            // Given
            sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
            when(orderRepository.findById(1L)).thenReturn(sampleOrder);
            when(orderRepository.updateStatus(1L, OrderStatus.CANCELLED)).thenReturn(1);

            // When
            orderService.cancelOrder(1L, 100L);

            // Then
            verify(orderRepository).updateStatus(1L, OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("异常场景：订单不属于当前用户，抛出 403 权限异常")
        void shouldThrowWhenOrderNotOwned() {
            // Given：订单属于 userId=100，但请求来自 userId=200
            when(orderRepository.findById(1L)).thenReturn(sampleOrder);

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 403)
                    .hasMessage("无权操作此订单");

            verify(orderRepository, never()).updateStatus(anyLong(), any());
        }

        @Test
        @DisplayName("异常场景：订单状态非待支付（已发货），不可取消")
        void shouldThrowWhenOrderNotPendingPayment() {
            // Given
            sampleOrder.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1L)).thenReturn(sampleOrder);

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 400)
                    .hasMessage("当前订单状态不可取消");

            verify(orderRepository, never()).updateStatus(anyLong(), any());
        }

        @Test
        @DisplayName("异常场景：订单不存在时抛出 404")
        void shouldThrowWhenOrderNotFoundForCancel() {
            when(orderRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.cancelOrder(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404);
        }
    }

    // ──────────────────── updateOrderStatus ────────────────────

    @Nested
    @DisplayName("updateOrderStatus - 更新订单状态")
    class UpdateOrderStatus {

        @Test
    @DisplayName("正常场景：订单存在时成功更新状态")
        void shouldUpdateStatusWhenOrderExists() {
            when(orderRepository.findById(1L)).thenReturn(sampleOrder);
            when(orderRepository.updateStatus(1L, OrderStatus.SHIPPED)).thenReturn(1);

            orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);

            verify(orderRepository).updateStatus(1L, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("异常场景：订单不存在时抛出 404，不执行更新")
        void shouldThrowWhenOrderNotFoundForStatusUpdate() {
            when(orderRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.updateOrderStatus(999L, OrderStatus.SHIPPED))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404);

            verify(orderRepository, never()).updateStatus(anyLong(), any());
        }
    }

    // ──────────────────── getAllOrders ────────────────────

    @Nested
    @DisplayName("getAllOrders - 查询所有订单（管理用）")
    class GetAllOrders {

        @Test
        @DisplayName("正常场景：返回全部订单列表")
        void shouldReturnAllOrders() {
            when(orderRepository.findAll()).thenReturn(List.of(sampleOrder));

            List<Order> result = orderService.getAllOrders();

            assertThat(result).hasSize(1);
            verify(orderRepository).findAll();
        }

        @Test
        @DisplayName("空数据：无订单时返回空列表")
        void shouldReturnEmptyList() {
            when(orderRepository.findAll()).thenReturn(Collections.emptyList());

            assertThat(orderService.getAllOrders()).isEmpty();
        }
    }
}
