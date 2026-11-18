package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.PaymentRecord;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.entity.enums.PaymentStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付结果落库与订单状态流转。
 *
 * <p>独立成 Bean 而不是放在支付服务内部，是为了让 {@code @Transactional} 通过代理生效
 * （内部自调用不会开启事务）。幂等核心：订单状态用条件更新
 * {@code WHERE status = PENDING_PAYMENT}，重复通知/重复查询不会二次变更。
 */
@Service
public class PaymentStateManager {

    private static final Logger log = LoggerFactory.getLogger(PaymentStateManager.class);

    /** 支付渠道：支付宝 */
    public static final String CHANNEL_ALIPAY = "ALIPAY";

    private static final int MAX_PAYLOAD_LENGTH = 2000;

    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentStateManager(OrderRepository orderRepository,
                               PaymentRecordRepository paymentRecordRepository) {
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    /** 支付状态流转结果 */
    public enum PaidResult {
        /** 本次调用完成了「待支付 → 已支付」 */
        APPLIED,
        /** 订单已是已支付状态，重复通知/查询，未重复变更 */
        ALREADY_PAID,
        /** 订单不存在、金额不一致或状态冲突，拒绝处理 */
        CONFLICT
    }

    /**
     * 标记订单支付成功（事务内完成状态流转与流水更新）。
     *
     * @param orderNo       订单号
     * @param tradeNo       支付宝交易号
     * @param paidAmount    支付宝侧金额（为空时跳过金额校验）
     * @param notifyPayload 回调原文（可空，截断保存）
     * @return 流转结果，调用方据此决定回调响应
     */
    @Transactional
    public PaidResult markPaid(String orderNo, String tradeNo, BigDecimal paidAmount, String notifyPayload) {
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            log.error("[ALIPAY-ALARM] 支付成功但订单不存在: orderNo={}, tradeNo={}", orderNo, tradeNo);
            return PaidResult.CONFLICT;
        }

        if (paidAmount != null && paidAmount.compareTo(order.getTotalAmount()) != 0) {
            log.error("[ALIPAY-ALARM] 支付金额与订单金额不一致，拒绝处理: orderNo={}, orderAmount={}, paidAmount={}",
                    orderNo, order.getTotalAmount(), paidAmount);
            return PaidResult.CONFLICT;
        }

        if (isPaidStatus(order.getStatus())) {
            // 幂等：已支付订单重复回调不再变更状态，仅尽量补齐流水中的交易号
            paymentRecordRepository.markPaid(CHANNEL_ALIPAY, orderNo, tradeNo,
                    truncate(notifyPayload), LocalDateTime.now());
            log.info("重复支付通知，订单已是已支付状态: orderNo={}, tradeNo={}", orderNo, tradeNo);
            return PaidResult.ALREADY_PAID;
        }

        int affected = orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        if (affected == 0) {
            // 条件更新失败：并发回调或订单已被取消，重新读取确认最终状态
            Order latest = orderRepository.findById(order.getId());
            if (latest != null && isPaidStatus(latest.getStatus())) {
                return PaidResult.ALREADY_PAID;
            }
            log.error("[ALIPAY-ALARM] 订单状态与支付结果冲突，拒绝处理: orderNo={}, currentStatus={}",
                    orderNo, latest == null ? "null" : latest.getStatus());
            return PaidResult.CONFLICT;
        }

        paymentRecordRepository.markPaid(CHANNEL_ALIPAY, orderNo, tradeNo,
                truncate(notifyPayload), LocalDateTime.now());
        log.info("支付宝支付成功: orderNo={}, tradeNo={}, amount={}", orderNo, tradeNo, paidAmount);
        return PaidResult.APPLIED;
    }

    /** 标记支付宝交易已关闭（超时未支付/用户取消），订单状态不变 */
    @Transactional
    public void markClosed(String orderNo) {
        paymentRecordRepository.markClosed(CHANNEL_ALIPAY, orderNo);
        log.info("支付宝交易已关闭: orderNo={}", orderNo);
    }

    /** 已支付及其后续状态（已发货/已完成）都视为支付完成 */
    public static boolean isPaidStatus(OrderStatus status) {
        return status == OrderStatus.PAID
                || status == OrderStatus.SHIPPED
                || status == OrderStatus.COMPLETED;
    }

    /** 通知原文可能很长，截断后入库便于排查 */
    public static String truncate(String payload) {
        if (payload == null) {
            return null;
        }
        return payload.length() <= MAX_PAYLOAD_LENGTH ? payload : payload.substring(0, MAX_PAYLOAD_LENGTH);
    }

    /** 仅供查询状态时判断流水是否关闭 */
    public static boolean isClosed(PaymentRecord record) {
        return record != null && record.getStatus() == PaymentStatus.CLOSED;
    }
}