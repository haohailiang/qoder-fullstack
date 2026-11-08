package com.ecommerce.product.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体
 */
@Data
public class Product {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String imageUrl;
    /** 状态：0下架 1上架 */
    private Integer status;
    /** 是否秒杀商品 */
    private Integer isSeckill;
    private BigDecimal seckillPrice;
    private LocalDateTime seckillStartTime;
    private LocalDateTime seckillEndTime;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
}
