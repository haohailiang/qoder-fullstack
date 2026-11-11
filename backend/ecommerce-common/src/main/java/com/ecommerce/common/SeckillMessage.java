package com.ecommerce.common;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀消息 DTO，用于 RabbitMQ 传递秒杀请求信息
 */
@Data
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 商品ID */
    private Long productId;
    /** 购买数量 */
    private Integer quantity;
    /** 商品名称（发送方快照，避免消费端再取商品服务） */
    private String productName;
    /** 成交单价（秒杀价，发送方快照） */
    private BigDecimal price;
    /** 订单号（预生成） */
    private String orderNo;
    /** 消息创建时间 */
    private LocalDateTime createTime;
}
