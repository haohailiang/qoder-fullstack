package com.ecommerce.order.entity.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态码获取枚举值
     * @param code 状态码
     * @return 对应的枚举值，如果未找到则返回 null
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据状态码获取描述
     * @param code 状态码
     * @return 对应的描述，如果未找到则返回 "未知状态"
     */
    public static String getDescriptionByCode(int code) {
        OrderStatus status = fromCode(code);
        return status != null ? status.description : "未知状态";
    }
}
