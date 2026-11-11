package com.ecommerce.order.consumer;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.common.SeckillMessage;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeckillOrderConsumer 单元测试：验证幂等跳过、真实库存扣减、金额快照与失败重试语义。
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    private SeckillOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SeckillOrderConsumer(orderRepository, productClient);
    }

    private SeckillMessage message() {
        SeckillMessage message = new SeckillMessage();
        message.setOrderNo("SK20261110120000ABCDEF");
        message.setUserId(100L);
        message.setProductId(2L);
        message.setQuantity(2);
        message.setProductName("MacBook Air M3");
        message.setPrice(new BigDecimal("7999.00"));
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    @Test
    @DisplayName("重复消息：订单号已落库时直接确认，不再扣库存")
    void shouldSkipWhenOrderAlreadyExists() {
        when(orderRepository.findByOrderNo("SK20261110120000ABCDEF")).thenReturn(new Order());

        consumer.onMessage(message());

        verify(productClient, never()).deductStock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("正常消费：按真实数量扣减 MySQL 库存，订单金额取价格快照 × 数量")
    void shouldDeductRealStockAndFillAmount() {
        when(orderRepository.findByOrderNo(any())).thenReturn(null);
        when(productClient.deductStock(2L, 2)).thenReturn(Result.success(true));

        consumer.onMessage(message());

        verify(productClient).deductStock(2L, 2);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("15998.00");
        assertThat(saved.getProductName()).isEqualTo("MacBook Air M3");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("库存不足：抛异常触发重试（最终进死信队列补偿），不落订单")
    void shouldThrowWhenDeductFails() {
        when(orderRepository.findByOrderNo(any())).thenReturn(null);
        when(productClient.deductStock(2L, 2)).thenReturn(Result.success(false));

        assertThatThrownBy(() -> consumer.onMessage(message()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("唯一索引冲突：视为已处理，不向上抛异常导致消息反复重试")
    void shouldSwallowDuplicateKey() {
        when(orderRepository.findByOrderNo(any())).thenReturn(null);
        when(productClient.deductStock(2L, 2)).thenReturn(Result.success(true));
        when(orderRepository.save(any(Order.class))).thenThrow(new DuplicateKeyException("uk_order_no"));

        consumer.onMessage(message());

        verify(orderRepository).save(any(Order.class));
    }
}
