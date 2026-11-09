package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.mapper.OrderMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderRepository 单元测试：验证 MyBatis Mapper 调用与数据转换逻辑。
 */
@ExtendWith(MockitoExtension.class)
class OrderRepositoryTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderRepository orderRepository;

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

    // ──────────────────── findByUserId ────────────────────

    @Nested
    @DisplayName("findByUserId - 按用户 ID 查询订单")
    class FindByUserId {

        @Test
        @DisplayName("正常场景：返回用户的订单列表")
        void shouldReturnOrdersForUser() {
            when(orderMapper.selectByUserId(100L)).thenReturn(List.of(sampleOrder));

            List<Order> result = orderRepository.findByUserId(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("空数据：用户无订单时返回空列表")
        void shouldReturnEmptyList() {
            when(orderMapper.selectByUserId(200L)).thenReturn(Collections.emptyList());

            assertThat(orderRepository.findByUserId(200L)).isEmpty();
        }
    }

    // ──────────────────── findById ────────────────────

    @Nested
    @DisplayName("findById - 按订单 ID 查询")
    class FindById {

        @Test
        @DisplayName("正常场景：ID 存在时返回订单")
        void shouldReturnOrderById() {
            when(orderMapper.selectById(1L)).thenReturn(sampleOrder);

            Order result = orderRepository.findById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getOrderNo()).isEqualTo("ORD20250101120000ABCDEF");
        }

        @Test
        @DisplayName("不存在：返回 null")
        void shouldReturnNullWhenNotFound() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThat(orderRepository.findById(999L)).isNull();
        }
    }

    // ──────────────────── findByOrderNo ────────────────────

    @Nested
    @DisplayName("findByOrderNo - 按订单号查询")
    class FindByOrderNo {

        @Test
        @DisplayName("正常场景：订单号存在时返回订单")
        void shouldReturnOrderByOrderNo() {
            when(orderMapper.selectByOrderNo("ORD20250101120000ABCDEF")).thenReturn(sampleOrder);

            Order result = orderRepository.findByOrderNo("ORD20250101120000ABCDEF");

            assertThat(result.getOrderNo()).isEqualTo("ORD20250101120000ABCDEF");
        }

        @Test
        @DisplayName("不存在：返回 null")
        void shouldReturnNullWhenOrderNoNotFound() {
            when(orderMapper.selectByOrderNo("NONEXIST")).thenReturn(null);

            assertThat(orderRepository.findByOrderNo("NONEXIST")).isNull();
        }
    }

    // ──────────────────── save ────────────────────

    @Nested
    @DisplayName("save - 保存订单（新增）")
    class Save {

        @Test
        @DisplayName("新增订单：调用 Mapper insert 方法并返回订单对象")
        void shouldInsertAndReturnOrder() {
            when(orderMapper.insert(any(Order.class))).thenReturn(1);

            Order result = orderRepository.save(sampleOrder);

            assertThat(result).isSameAs(sampleOrder);
            verify(orderMapper).insert(sampleOrder);
        }

        @Test
        @DisplayName("新增订单：验证传入参数正确传递到 Mapper")
        void shouldPassCorrectOrderToMapper() {
            when(orderMapper.insert(any(Order.class))).thenReturn(1);

            Order newOrder = new Order();
            newOrder.setOrderNo("ORD-NEW");
            newOrder.setUserId(200L);
            newOrder.setProductId(5L);
            newOrder.setQuantity(1);
            newOrder.setTotalAmount(new BigDecimal("99.99"));
            newOrder.setStatus(OrderStatus.PENDING_PAYMENT);

            orderRepository.save(newOrder);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).insert(captor.capture());
            assertThat(captor.getValue().getOrderNo()).isEqualTo("ORD-NEW");
            assertThat(captor.getValue().getUserId()).isEqualTo(200L);
        }
    }

    // ──────────────────── updateStatus ────────────────────

    @Nested
    @DisplayName("updateStatus - 更新订单状态")
    class UpdateStatus {

        @Test
        @DisplayName("正常场景：更新状态并返回受影响行数")
        void shouldUpdateStatusAndReturnAffectedRows() {
            when(orderMapper.updateStatus(1L, OrderStatus.SHIPPED)).thenReturn(1);

            int affected = orderRepository.updateStatus(1L, OrderStatus.SHIPPED);

            assertThat(affected).isEqualTo(1);
            verify(orderMapper).updateStatus(1L, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("异常场景：订单不存在时受影响行数为 0")
        void shouldReturnZeroWhenOrderNotFound() {
            when(orderMapper.updateStatus(999L, OrderStatus.CANCELLED)).thenReturn(0);

            int affected = orderRepository.updateStatus(999L, OrderStatus.CANCELLED);

            assertThat(affected).isEqualTo(0);
        }
    }

    // ──────────────────── findAll ────────────────────

    @Nested
    @DisplayName("findAll - 查询所有订单")
    class FindAll {

        @Test
        @DisplayName("正常场景：返回全部订单列表")
        void shouldReturnAllOrders() {
            when(orderMapper.selectAll()).thenReturn(List.of(sampleOrder));

            List<Order> result = orderRepository.findAll();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("空数据：无订单时返回空列表")
        void shouldReturnEmptyList() {
            when(orderMapper.selectAll()).thenReturn(Collections.emptyList());

            assertThat(orderRepository.findAll()).isEmpty();
        }
    }
}
