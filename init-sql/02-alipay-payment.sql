-- 支付宝沙箱支付：支付流水表（可重复执行）
-- 适用于已初始化过 01-init-database.sql 的数据库；新库按文件名顺序 01 -> 02 自动执行。
SET NAMES utf8mb4;

USE qoder_fullstack;

-- 支付流水表：一个订单一个支付渠道一条流水
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