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
