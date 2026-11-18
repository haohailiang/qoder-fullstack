package com.ecommerce.order.entity;

import com.ecommerce.order.entity.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水实体（一个订单一个渠道一条流水）
 */
@Data
public class PaymentRecord {

    private Long id;
    /** 订单号 */
    private String orderNo;
    /** 支付渠道，如 ALIPAY */
    private String channel;
    /** 支付宝交易号（支付成功后回填） */
    private String tradeNo;
    /** 支付金额 */
    private BigDecimal amount;
    /** 支付状态 */
    private PaymentStatus status;
    /** 最近一次异步通知原文（截断保存，便于排查） */
    private String notifyPayload;
    /** 支付成功时间 */
    private LocalDateTime paidTime;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}