package com.ecommerce.product.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 航班实体（机票商品特有字段）
 */
@Data
public class Flight {

    private Long id;
    private Long productId;
    private String departureCity;
    private String arrivalCity;
    private BigDecimal departureLng;
    private BigDecimal departureLat;
    private BigDecimal arrivalLng;
    private BigDecimal arrivalLat;
    private String flightNumber;
    /** 飞行时长（分钟） */
    private Integer duration;
    /** 每周航班频率 */
    private Integer frequency;
    /** 途经城市坐标 JSON 数组，如 [{"name":"济南","lng":117.0,"lat":36.67}] */
    private String routeCoordinates;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;

    // ---- 关联商品字段（非 flights 表列，由 JOIN 查询填充） ----
    private String productName;
    private String productDescription;
    private BigDecimal productPrice;
    private Integer productStock;
    private String productImageUrl;
    private Integer productStatus;
}
