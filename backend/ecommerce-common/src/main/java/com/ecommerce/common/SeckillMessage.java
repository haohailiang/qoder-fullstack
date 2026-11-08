package com.ecommerce.common;

import lombok.Data;
import java.io.Serializable;
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
    /** 订单号（预生成） */
    private String orderNo;
    /** 消息创建时间 */
    private LocalDateTime createTime;
}
