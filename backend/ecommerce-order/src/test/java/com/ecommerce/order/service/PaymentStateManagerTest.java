package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRecordRepository;
import com.ecommerce.order.service.PaymentStateManager.PaidResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PaymentStateManager 单元测试：支付成功的幂等落库与订单状态流转。
 *
 * <p>覆盖重复通知、金额不一致、订单不存在、并发条件更新失败等分支，
 * 这些是「重复通知不得二次变更状态」的关键保障点。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentStateManager - 支付结果落库与状态流转")
class PaymentStateManagerTest {

    private static final String ORDER_NO = "ORD20250101120000ABCDEF";
    private static final String TRADE_NO = "2025010122001440000000000001";
    private static final BigDecimal AMOUNT = new BigDecimal("199.98");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    private PaymentStateManager stateManager;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        stateManager = new PaymentStateManager(orderRepository, paymentRecordRepository);
        pendingOrder = buildOrder(1L, OrderStatus.PENDING_PAYMENT);
    }

    private static Order buildOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(ORDER_NO);
        order.setUserId(100L);
        order.setTotalAmount(AMOUNT);
        order.setStatus(status);
        return order;
    }

    @Nested
    @DisplayName("markPaid - 标记支付成功")
    class MarkPaid {

        @Test
        @DisplayName("正常场景：待支付订单条件更新为已支付，并回填支付流水")
        void shouldApplyWhenOrderPending() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);
            when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).thenReturn(1);

            PaidResult result = stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, "{\"trade_status\":\"TRADE_SUCCESS\"}");

            assertThat(result).isEqualTo(PaidResult.APPLIED);
            verify(paymentRecordRepository).markPaid(eq("ALIPAY"), eq(ORDER_NO), eq(TRADE_NO),
                    any(String.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("重复通知：订单已是已支付，不再做状态变更")
        void shouldBeIdempotentWhenAlreadyPaid() {
            Order paidOrder = buildOrder(1L, OrderStatus.PAID);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(paidOrder);

            PaidResult result = stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null);

            assertThat(result).isEqualTo(PaidResult.ALREADY_PAID);
            verify(orderRepository, never())
                    .updateStatusIfCurrent(any(), any(OrderStatus.class), any(OrderStatus.class));
        }

        @Test
        @DisplayName("已发货订单重复回调：同样视为已支付（幂等）")
        void shouldBeIdempotentWhenShipped() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(buildOrder(1L, OrderStatus.SHIPPED));

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null))
                    .isEqualTo(PaidResult.ALREADY_PAID);
            verify(orderRepository, never())
                    .updateStatusIfCurrent(any(), any(OrderStatus.class), any(OrderStatus.class));
        }

        @Test
        @DisplayName("并发场景：条件更新影响 0 行但订单已被改为已支付，视为重复通知")
        void shouldTreatAsAlreadyPaidWhenConditionUpdateMisses() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);
            when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).thenReturn(0);
            when(orderRepository.findById(1L)).thenReturn(buildOrder(1L, OrderStatus.PAID));

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null))
                    .isEqualTo(PaidResult.ALREADY_PAID);
        }

        @Test
        @DisplayName("冲突场景：订单已取消，条件更新失败后拒绝处理且不写流水")
        void shouldRejectWhenOrderCancelled() {
            Order cancelled = buildOrder(1L, OrderStatus.CANCELLED);
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(cancelled);
            // 条件更新带 status=PENDING_PAYMENT 限制，已取消订单影响 0 行
            when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).thenReturn(0);
            when(orderRepository.findById(1L)).thenReturn(cancelled);

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null))
                    .isEqualTo(PaidResult.CONFLICT);
            verify(orderRepository).updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
            verifyNoInteractions(paymentRecordRepository);
        }

        @Test
        @DisplayName("金额不一致：拒绝处理")
        void shouldRejectWhenAmountMismatched() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, new BigDecimal("0.01"), null))
                    .isEqualTo(PaidResult.CONFLICT);
            verify(orderRepository, never())
                    .updateStatusIfCurrent(any(), any(OrderStatus.class), any(OrderStatus.class));
        }
        @Test
        @DisplayName("订单不存在：拒绝处理")
        void shouldRejectWhenOrderMissing() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(null);

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null))
                    .isEqualTo(PaidResult.CONFLICT);
            verifyNoInteractions(paymentRecordRepository);
        }

        @Test
        @DisplayName("金额为空（查单接口未返回金额）：跳过金额校验")
        void shouldSkipAmountCheckWhenNull() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);
            when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).thenReturn(1);

            assertThat(stateManager.markPaid(ORDER_NO, TRADE_NO, null, null))
                    .isEqualTo(PaidResult.APPLIED);
        }
    }

    @Nested
    @DisplayName("markClosed - 关闭交易")
    class MarkClosed {

        @Test
        @DisplayName("标记流水关闭，订单状态不变")
        void shouldOnlyMarkRecordClosed() {
            stateManager.markClosed(ORDER_NO);

            verify(paymentRecordRepository).markClosed("ALIPAY", ORDER_NO);
            verify(orderRepository, never()).updateStatus(any(), any(OrderStatus.class));
        }
    }

    @Nested
    @DisplayName("工具方法")
    class Helpers {

        @Test
        @DisplayName("truncate：超长回调原文截断，null 原样返回")
        void shouldTruncatePayload() {
            assertThat(PaymentStateManager.truncate(null)).isNull();
            assertThat(PaymentStateManager.truncate("short")).isEqualTo("short");

            String longPayload = "x".repeat(2500);
            assertThat(PaymentStateManager.truncate(longPayload)).hasSize(2000);
        }

        @Test
        @DisplayName("isPaidStatus：仅已支付/已发货/已完成视为支付完成")
        void shouldDetectPaidStatus() {
            assertThat(PaymentStateManager.isPaidStatus(OrderStatus.PAID)).isTrue();
            assertThat(PaymentStateManager.isPaidStatus(OrderStatus.SHIPPED)).isTrue();
            assertThat(PaymentStateManager.isPaidStatus(OrderStatus.COMPLETED)).isTrue();
            assertThat(PaymentStateManager.isPaidStatus(OrderStatus.PENDING_PAYMENT)).isFalse();
            assertThat(PaymentStateManager.isPaidStatus(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("isClosed：流水为空或非 CLOSED 时返回 false")
        void shouldDetectClosedRecord() {
            assertThat(PaymentStateManager.isClosed(null)).isFalse();
            assertThat(PaymentStateManager.isClosed(new com.ecommerce.order.entity.PaymentRecord())).isFalse();
        }
    }

    @Nested
    @DisplayName("markPaid - 通知原文为空")
    class NullPayload {

        @Test
        @DisplayName("payload 为空时以 null 落库，不抛空指针")
        void shouldAcceptNullPayload() {
            when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(pendingOrder);
            when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).thenReturn(1);

            stateManager.markPaid(ORDER_NO, TRADE_NO, AMOUNT, null);

            verify(paymentRecordRepository).markPaid(eq("ALIPAY"), eq(ORDER_NO), eq(TRADE_NO),
                    isNull(), any(LocalDateTime.class));
        }
    }
}