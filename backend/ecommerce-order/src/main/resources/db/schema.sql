-- 强制本次会话按 UTF-8 解释脚本内容，避免客户端按 latin1 处理本文件
-- 导致中文注释出现双重编码乱码。
SET NAMES utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(50)     NOT NULL COMMENT '订单号（唯一）',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    product_id      BIGINT          NOT NULL COMMENT '商品ID',
    product_name    VARCHAR(255)    COMMENT '商品名称（冗余）',
    quantity        INT             NOT NULL COMMENT '购买数量',
    total_amount    DECIMAL(10,2)   NOT NULL COMMENT '订单总金额',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0待支付 1已支付 2已发货 3已完成 4已取消',
    address         VARCHAR(500)    COMMENT '收货地址',
    remark          VARCHAR(500)    COMMENT '备注',
    created_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 支付流水表（支付宝沙箱支付，与 init-sql/02-alipay-payment.sql 保持一致）
CREATE TABLE IF NOT EXISTS payment_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(50)     NOT NULL COMMENT '订单号',
    channel         VARCHAR(20)     NOT NULL DEFAULT 'ALIPAY' COMMENT '支付渠道',
    trade_no        VARCHAR(64)     NULL COMMENT '支付宝交易号（支付成功后回填）',
    amount          DECIMAL(10,2)   NOT NULL COMMENT '支付金额',
    status          VARCHAR(20)     NOT NULL COMMENT '状态: CREATED/PAID/FAILED/CLOSED',
    notify_payload  TEXT            NULL COMMENT '最近一次异步通知原文（截断保存）',
    paid_time       DATETIME        NULL COMMENT '支付成功时间',
    created_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_date    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_channel_order (channel, order_no),
    UNIQUE KEY uk_trade_no (trade_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';
