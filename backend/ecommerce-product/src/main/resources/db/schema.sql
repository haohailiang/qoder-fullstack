-- 强制本次会话按 UTF-8 解释脚本内容，避免客户端按 latin1 处理本文件
-- 导致中文注释出现双重编码乱码。
SET NAMES utf8mb4;

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL COMMENT '商品名称',
    description     TEXT            COMMENT '商品描述',
    price           DECIMAL(10,2)   NOT NULL COMMENT '价格',
    stock           INT             NOT NULL DEFAULT 0 COMMENT '库存',
    category        VARCHAR(100)    COMMENT '分类',
    image_url       VARCHAR(500)    COMMENT '图片URL',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0下架 1上架',
    is_seckill      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否秒杀商品',
    seckill_price   DECIMAL(10,2)   COMMENT '秒杀价格',
    seckill_start_time DATETIME     COMMENT '秒杀开始时间',
    seckill_end_time   DATETIME     COMMENT '秒杀结束时间',
    created_by      VARCHAR(100)    COMMENT '创建人',
    created_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_modified_by VARCHAR(100)   COMMENT '最后修改人',
    last_modified_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_is_seckill (is_seckill)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 航班表（机票商品特有字段）
CREATE TABLE IF NOT EXISTS flights (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT          NOT NULL COMMENT '关联商品ID',
    departure_city      VARCHAR(100)    NOT NULL COMMENT '出发城市',
    arrival_city        VARCHAR(100)    NOT NULL COMMENT '落地城市',
    departure_lng       DECIMAL(10,4)   NOT NULL COMMENT '出发城市经度',
    departure_lat       DECIMAL(10,4)   NOT NULL COMMENT '出发城市纬度',
    arrival_lng         DECIMAL(10,4)   NOT NULL COMMENT '落地城市经度',
    arrival_lat         DECIMAL(10,4)   NOT NULL COMMENT '落地城市纬度',
    flight_number       VARCHAR(50)     COMMENT '航班号',
    duration            INT             COMMENT '飞行时长(分钟)',
    frequency           INT             NOT NULL DEFAULT 1 COMMENT '每周航班频率',
    route_coordinates   JSON            COMMENT '途经城市坐标 [{name,lng,lat}]',
    created_date        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_modified_date  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_product_id (product_id),
    INDEX idx_departure_city (departure_city),
    INDEX idx_arrival_city (arrival_city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='航班表';
