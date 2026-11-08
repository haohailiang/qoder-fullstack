package com.ecommerce.order.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
public class Order {

    private Long id;
    /** 订单号（唯一） */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** 商品ID */
    private Long productId;
    /** 商品名称（冗余） */
    private String productName;
    /** 购买数量 */
    private Integer quantity;
    /** 订单总金额 */
    private BigDecimal totalAmount;
    /**
     * 订单状态：
     * 0 - 待支付
     * 1 - 已支付
     * 2 - 已发货
     * 3 - 已完成
     * 4 - 已取消
     */
    private Integer status;
    /** 收货地址 */
    private String address;
    /** 备注 */
    private String remark;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
