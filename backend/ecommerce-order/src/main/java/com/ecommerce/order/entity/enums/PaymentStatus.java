package com.ecommerce.order.entity.enums;

import lombok.Getter;

/**
 * 支付流水状态枚举（落库使用枚举名，MyBatis 默认 EnumTypeHandler 映射）
 */
@Getter
public enum PaymentStatus {

    CREATED("已创建支付"),
    PAID("支付成功"),
    FAILED("支付失败"),
    CLOSED("交易关闭");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }
}