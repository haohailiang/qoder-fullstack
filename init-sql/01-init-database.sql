-- 强制本次会话按 UTF-8 解释脚本内容。
-- 原因：/docker-entrypoint-initdb.d 自动执行、或 docker exec 里的 mysql 客户端，
-- 在容器 LANG 为 C/POSIX 时 character_set_client 默认取 latin1，
-- 会把本文件里合法的 UTF-8 字节当作 latin1 字符再转成 utf8mb4 存盘，
-- 造成中文数据和全部表/列注释出现“双重编码”乱码（管理员 -> ç®¡ç†å‘˜）。
-- 加了这句后，客户端即使默认 latin1 也不会再转坏。
SET NAMES utf8mb4;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS demo_fullstack DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE demo_fullstack;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(255)    NOT NULL COMMENT '密码（BCrypt 加密）',
    name            VARCHAR(100)    NOT NULL COMMENT '姓名',
    email           VARCHAR(200)    NOT NULL COMMENT '邮箱',
    phone           VARCHAR(20)     NOT NULL COMMENT '手机号',
    age             INT             COMMENT '年龄',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    created_by      VARCHAR(100)    COMMENT '创建人',
    created_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_modified_by VARCHAR(100)   COMMENT '最后修改人',
    last_modified_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    theme_preference VARCHAR(10)    NOT NULL DEFAULT 'dark' COMMENT '主题偏好：dark/light',
    language_preference VARCHAR(10) NOT NULL DEFAULT 'zh' COMMENT '语言偏好：zh/en',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL COMMENT '商品名称',
    description     TEXT            COMMENT '商品描述',
    price           DECIMAL(10,2)   NOT NULL COMMENT '价格',
    stock           INT             NOT NULL DEFAULT 0 COMMENT '库存',
    category        VARCHAR(100)    COMMENT '分类',
    image_url       VARCHAR(500)    COMMENT '图片URL',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0下架 1上架',
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

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(50)     NOT NULL COMMENT '订单号（唯一）',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    product_id      BIGINT          NOT NULL COMMENT '商品ID',
    product_name    VARCHAR(255)    COMMENT '商品名称（冗余）',
    quantity        INT             NOT NULL COMMENT '购买数量',
    total_amount    DECIMAL(10,2)   NOT NULL COMMENT '订单总金额',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已发货 3已完成 4已取消',
    address         VARCHAR(500)    COMMENT '收货地址',
    remark          VARCHAR(500)    COMMENT '备注',
    created_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

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

-- 插入演示数据
INSERT INTO users (username, password, name, email, phone, age) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', 'admin@ecommerce.com', '13800000001', 30),
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', 'zhangsan@ecommerce.com', '13800000002', 25),
('lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', 'lisi@ecommerce.com', '13800000003', 28);

INSERT INTO products (name, description, price, stock, category, status, is_seckill, seckill_price, seckill_start_time, seckill_end_time) VALUES
('iPhone 15 Pro', '苹果最新旗舰手机，A17 Pro 芯片', 7999.00, 500, 'electronics', 1, 0, NULL, NULL, NULL),
('MacBook Air M3', '轻薄笔记本，M3 芯片，15英寸', 9999.00, 200, 'electronics', 1, 1, 7999.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
('AirPods Pro 2', '主动降噪无线耳机', 1899.00, 1000, 'electronics', 1, 0, NULL, NULL, NULL),
('优衣库联名T恤', '设计师联名款纯棉T恤', 99.00, 2000, 'clothing', 1, 1, 49.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
('Java编程思想', '经典Java入门书籍', 109.00, 300, 'books', 1, 0, NULL, NULL, NULL),
('有机坚果礼盒', '精选混合坚果 1kg 装', 128.00, 800, 'food', 1, 1, 68.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59');

-- 插入机票商品（category = 'flight'）
INSERT INTO products (name, description, price, stock, category, status, is_seckill, seckill_price, seckill_start_time, seckill_end_time) VALUES
('北京→上海 机票', '北京首都国际机场至上海虹桥国际机场，CA1234 航班', 1280.00, 100, 'flight', 1, 0, NULL, NULL, NULL),
('广州→成都 机票', '广州白云国际机场至成都双流国际机场，CZ3456 航班', 980.00, 80, 'flight', 1, 0, NULL, NULL, NULL),
('深圳→北京 机票', '深圳宝安国际机场至北京首都国际机场，MU5678 航班', 1580.00, 60, 'flight', 1, 0, NULL, NULL, NULL),
('上海→广州 机票', '上海虹桥国际机场至广州白云国际机场，MU9012 航班', 1180.00, 90, 'flight', 1, 0, NULL, NULL, NULL),
('成都→上海 机票', '成都双流国际机场至上海虹桥国际机场，CA3456 航班', 1380.00, 70, 'flight', 1, 0, NULL, NULL, NULL),
('北京→广州 机票', '北京首都国际机场至广州白云国际机场，CZ5678 航班', 1680.00, 50, 'flight', 1, 0, NULL, NULL, NULL),
('上海→成都 机票', '上海虹桥国际机场至成都双流国际机场，MU7890 航班', 1480.00, 65, 'flight', 1, 0, NULL, NULL, NULL),
('深圳→上海 机票', '深圳宝安国际机场至上海虹桥国际机场，CA9012 航班', 1280.00, 85, 'flight', 1, 0, NULL, NULL, NULL);

-- 插入航班数据（关联上面插入的机票商品，product_id 从 7 开始）
INSERT INTO flights (product_id, departure_city, arrival_city, departure_lng, departure_lat, arrival_lng, arrival_lat, flight_number, duration, frequency, route_coordinates) VALUES
(7,  '北京', '上海', 116.4074, 39.9042, 121.4737, 31.2304, 'CA1234', 135, 28, '[{"name":"济南","lng":117.0009,"lat":36.6758}]'),
(8,  '广州', '成都', 113.2644, 23.1291, 104.0668, 30.5728, 'CZ3456', 150, 21, '[{"name":"重庆","lng":106.5516,"lat":29.5630}]'),
(9,  '深圳', '北京', 114.0579, 22.5431, 116.4074, 39.9042, 'MU5678', 180, 14, '[{"name":"武汉","lng":114.3054,"lat":30.5931}]'),
(10, '上海', '广州', 121.4737, 31.2304, 113.2644, 23.1291, 'MU9012', 150, 21, '[{"name":"厦门","lng":118.0894,"lat":24.4798}]'),
(11, '成都', '上海', 104.0668, 30.5728, 121.4737, 31.2304, 'CA3456', 165, 14, '[{"name":"武汉","lng":114.3054,"lat":30.5931}]'),
(12, '北京', '广州', 116.4074, 39.9042, 113.2644, 23.1291, 'CZ5678', 195, 7,  '[{"name":"长沙","lng":112.9388,"lat":28.2282}]'),
(13, '上海', '成都', 121.4737, 31.2304, 104.0668, 30.5728, 'MU7890', 180, 14, '[{"name":"重庆","lng":106.5516,"lat":29.5630}]'),
(14, '深圳', '上海', 114.0579, 22.5431, 121.4737, 31.2304, 'CA9012', 150, 21, '[{"name":"杭州","lng":120.1551,"lat":30.2741}]');
