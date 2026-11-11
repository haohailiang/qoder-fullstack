package com.ecommerce.product.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.SeckillMessage;
import com.ecommerce.common.Result;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.SeckillStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static com.ecommerce.common.config.SeckillRabbitConfig.SECKILL_EXCHANGE;
import static com.ecommerce.common.config.SeckillRabbitConfig.SECKILL_ROUTING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeckillController 单元测试：验证秒杀抢购的库存返回值语义、价格快照，
 * 以及「MQ 发送失败必须回滚 Redis 预扣库存、且不得写入购买标记」这条补偿链路。
 */
@ExtendWith(MockitoExtension.class)
class SeckillControllerTest {

    private static final long PRODUCT_ID = 2L;
    private static final long USER_ID = 100L;

    @Mock
    private SeckillStockService seckillStockService;

    @Mock
    private ProductService productService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private SeckillController controller;

    @BeforeEach
    void setUp() {
        controller = new SeckillController(seckillStockService, productService, rabbitTemplate);
    }

    /** 构造一个处于有效期内的秒杀商品 */
    private Product seckillProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("MacBook Air M3");
        product.setPrice(new BigDecimal("9999.00"));
        product.setIsSeckill(1);
        product.setSeckillPrice(new BigDecimal("7999.00"));
        product.setSeckillStartTime(LocalDateTime.now().minusHours(1));
        product.setSeckillEndTime(LocalDateTime.now().plusHours(1));
        return product;
    }

    private MockHttpServletRequest authedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", String.valueOf(USER_ID));
        return request;
    }

    @Nested
    @DisplayName("seckill - 抢购主流程")
    class Seckill {

        @Test
        @DisplayName("库存不足（Lua 返回 -2）：报错且不发消息、不回滚")
        void shouldRejectWhenStockInsufficient() {
            when(productService.getProductById(PRODUCT_ID)).thenReturn(seckillProduct());
            when(seckillStockService.tryDecrStock(PRODUCT_ID, 1))
                    .thenReturn(SeckillStockService.RESULT_INSUFFICIENT);

            assertThatThrownBy(() -> controller.seckill(PRODUCT_ID, 1, authedRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");

            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
            verify(seckillStockService, never()).markBought(anyLong(), anyLong());
            // Lua 未扣减成功，无库存需要回滚
            verify(seckillStockService, never()).rollbackStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("刚好售罄（Lua 返回 0）：属于扣减成功，正常发送消息")
        void shouldAcceptWhenLastPieceSoldOut() {
            when(productService.getProductById(PRODUCT_ID)).thenReturn(seckillProduct());
            when(seckillStockService.tryDecrStock(PRODUCT_ID, 1)).thenReturn(0L);

            Result<Map<String, Object>> result = controller.seckill(PRODUCT_ID, 1, authedRequest());

            assertThat(result.getData()).containsEntry("status", "QUEUING");
            verify(rabbitTemplate).convertAndSend(eq(SECKILL_EXCHANGE), eq(SECKILL_ROUTING_KEY), any(Object.class));
            verify(seckillStockService).markBought(PRODUCT_ID, USER_ID);
        }

        @Test
        @DisplayName("消息携带商品名与秒杀价快照，供订单服务直接计算金额")
        void shouldCarryProductNameAndPriceSnapshot() {
            when(productService.getProductById(PRODUCT_ID)).thenReturn(seckillProduct());
            when(seckillStockService.tryDecrStock(PRODUCT_ID, 2)).thenReturn(8L);

            controller.seckill(PRODUCT_ID, 2, authedRequest());

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(eq(SECKILL_EXCHANGE), eq(SECKILL_ROUTING_KEY), captor.capture());
            SeckillMessage message = (SeckillMessage) captor.getValue();
            assertThat(message.getProductName()).isEqualTo("MacBook Air M3");
            assertThat(message.getPrice()).isEqualByComparingTo("7999.00");
            assertThat(message.getQuantity()).isEqualTo(2);
            assertThat(message.getUserId()).isEqualTo(USER_ID);
            assertThat(message.getOrderNo()).startsWith("SK");
        }

        @Test
        @DisplayName("MQ 发送失败：回滚 Redis 库存，且不写购买标记（用户可重试）")
        void shouldRollbackStockWhenMqSendFails() {
            when(productService.getProductById(PRODUCT_ID)).thenReturn(seckillProduct());
            when(seckillStockService.tryDecrStock(PRODUCT_ID, 1)).thenReturn(9L);
            // convertAndSend 是 void 方法，只能用 doThrow 打桩
            doThrow(new AmqpException("broker down"))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            assertThatThrownBy(() -> controller.seckill(PRODUCT_ID, 1, authedRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("抢购失败");

            verify(seckillStockService).rollbackStock(PRODUCT_ID, 1);
            verify(seckillStockService, never()).markBought(anyLong(), anyLong());
        }

        @Test
        @DisplayName("未携带用户身份头：401 且不扣减库存")
        void shouldRejectWhenIdentityMissing() {
            MockHttpServletRequest anonymous = new MockHttpServletRequest();

            assertThatThrownBy(() -> controller.seckill(PRODUCT_ID, 1, anonymous))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未授权");

            verify(seckillStockService, never()).tryDecrStock(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("rollback - 死信补偿")
    class Rollback {

        @Test
        @DisplayName("回滚库存并清除购买标记")
        void shouldRollbackStockAndClearBought() {
            controller.rollback(PRODUCT_ID, 3, USER_ID);

            verify(seckillStockService).rollbackStock(PRODUCT_ID, 3);
            verify(seckillStockService).clearBought(PRODUCT_ID, USER_ID);
        }
    }
}
