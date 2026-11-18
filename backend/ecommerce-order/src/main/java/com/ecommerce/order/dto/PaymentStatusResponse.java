package com.ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态返回体：供支付结果页轮询与订单列表刷新
 */
@Data
public class PaymentStatusResponse {

    private String orderNo;
    /** 订单状态码：0待支付 1已支付 2已发货 3已完成 4已取消 */
    private Integer orderStatus;
    /** 支付状态：PAID / PENDING / CLOSED / CANCELLED / FAILED */
    private String payStatus;
    private String tradeNo;
    private BigDecimal amount;
    private LocalDateTime paidTime;
    /** 面向用户的提示信息 */
    private String message;
}