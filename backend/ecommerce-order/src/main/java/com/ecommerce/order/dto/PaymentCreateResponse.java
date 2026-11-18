package com.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建支付返回体：前端拿到 payUrl 后整页跳转到支付宝收银台
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateResponse {

    private String orderNo;
    /** 支付渠道 */
    private String channel;
    /** 待支付金额 */
    private BigDecimal amount;
    /** 支付宝收银台地址（已带签名参数） */
    private String payUrl;
    /** 支付超时时间表达式，如 30m */
    private String timeoutExpress;
}
