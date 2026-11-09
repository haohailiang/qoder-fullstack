package com.ecommerce.order.client;

import com.ecommerce.common.Result;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ProductClient Feign 接口测试：验证 OrderServiceImpl 在调用商品服务 Feign 接口时的
 * 各种响应场景，包括成功、失败、超时、返回 null 等边界条件。
 * 本测试聚焦于跨服务调用的容错与异常处理逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    @Mock
    private ProductClient productClient;

    @Mock
    private com.ecommerce.order.repository.OrderRepository orderRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, productClient);
    }

    @Nested
    @DisplayName("deductStock - 库存扣减 Feign 调用场景")
    class DeductStock {

        @Test
        @DisplayName("正常场景：Feign 返回 Result.success(true)，库存扣减成功")
        void shouldSucceedWhenFeignReturnsTrue() {
            // Given
            when(productClient.deductStock(eq(10L), eq(2)))
                    .thenReturn(Result.success(true));
            when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                    .thenAnswer(invocation -> {
                        Order o = invocation.getArgument(0);
                        o.setId(1L);
                        return o;
                    });

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(2);
            order.setTotalAmount(new BigDecimal("199.98"));
            order.setAddress("北京市朝阳区");

            // When
            Order result = orderService.createOrder(100L, order);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("异常场景：Feign 返回 Result.success(false)，库存不足")
        void shouldFailWhenFeignReturnsFalse() {
            // Given
            when(productClient.deductStock(eq(10L), eq(100)))
                    .thenReturn(Result.success(false));

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(100);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, order))
                    .isInstanceOf(com.ecommerce.common.BusinessException.class)
                    .hasMessage("库存不足或商品不存在");
        }

        @Test
        @DisplayName("异常场景：Feign 返回 null，视为扣减失败")
        void shouldFailWhenFeignReturnsNull() {
            // Given
            when(productClient.deductStock(eq(10L), eq(1)))
                    .thenReturn(null);

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(1);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, order))
                    .isInstanceOf(com.ecommerce.common.BusinessException.class)
                    .hasMessage("库存不足或商品不存在");
        }

        @Test
        @DisplayName("异常场景：Feign 调用超时/网络异常，包装为 500 业务异常")
        void shouldFailWhenFeignThrowsException() {
            // Given
            when(productClient.deductStock(eq(10L), eq(1)))
                    .thenThrow(new RuntimeException("Read timed out"));

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(1);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, order))
                    .isInstanceOf(com.ecommerce.common.BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 500)
                    .hasMessage("库存扣减失败，请稍后重试");
        }

        @Test
        @DisplayName("异常场景：Feign 返回 Result 但 data 为 null，视为扣减失败")
        void shouldFailWhenResultDataIsNull() {
            // Given
            when(productClient.deductStock(eq(10L), eq(1)))
                    .thenReturn(Result.success(null));

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(1);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, order))
                    .isInstanceOf(com.ecommerce.common.BusinessException.class)
                    .hasMessage("库存不足或商品不存在");
        }

        @Test
        @DisplayName("异常场景：Feign 返回错误 Result（code=500），data 为 null 视为扣减失败")
        void shouldFailWhenResultCodeIsError() {
            // Given
            when(productClient.deductStock(eq(10L), eq(1)))
                    .thenReturn(Result.error(500, "商品服务内部错误"));

            Order order = new Order();
            order.setProductId(10L);
            order.setQuantity(1);

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(100L, order))
                    .isInstanceOf(com.ecommerce.common.BusinessException.class)
                    .hasMessage("库存不足或商品不存在");
        }
    }
}
