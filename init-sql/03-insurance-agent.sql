-- 保险商品 + 保险咨询机器人（ai-service）数据表
-- 用法（已初始化的库，直接执行本文件即可，幂等）：
--   docker exec -i ecommerce-mysql mysql -uroot -proot < init-sql/03-insurance-agent.sql
-- 说明：ai-service 启动时也会用 SQLAlchemy create_all 自动建表，本文件用于全新初始化与手工检查。
SET NAMES utf8mb4;

USE qoder_fullstack;

-- ==================== 保险商品（复用 products 表） ====================
INSERT INTO products (name, description, price, stock, category, status, is_seckill)
SELECT '安心百万医疗险', '百万医疗住院报销，覆盖住院、门诊手术、特殊门诊与住院前后门急诊；年度免赔额 1 万元，经社保结算后赔付比例 100%。具体保障范围、免责条款与理赔材料以上传的保险条款 PDF 为准。', 299.00, 9999, 'insurance', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '安心百万医疗险');

INSERT INTO products (name, description, price, stock, category, status, is_seckill)
SELECT '守护重疾险', '覆盖 120 种重大疾病与 40 种轻症，确诊即给付；等待期 90 天，保障期可选 30 年或至 70 周岁。具体疾病定义、等待期与免责条款以上传的保险条款 PDF 为准。', 1599.00, 9999, 'insurance', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '守护重疾险');

INSERT INTO products (name, description, price, stock, category, status, is_seckill)
SELECT '出行无忧意外险', '全年综合意外保障，含意外身故/伤残、意外医疗与住院津贴；保费低廉，适合日常通勤与短途出行。具体保额、免赔额与免责条款以上传的保险条款 PDF 为准。', 39.00, 9999, 'insurance', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '出行无忧意外险');

-- ==================== 知识库文档 ====================
CREATE TABLE IF NOT EXISTS ai_insurance_document (
    id                  VARCHAR(32)     NOT NULL COMMENT '文档ID（UUID hex）',
    product_id          BIGINT          NOT NULL COMMENT '关联保险商品ID',
    filename            VARCHAR(255)    NOT NULL COMMENT '原始 PDF 文件名',
    file_path           VARCHAR(500)    NOT NULL COMMENT '本地落盘路径',
    file_size           BIGINT          NOT NULL DEFAULT 0 COMMENT '文件字节数',
    status              VARCHAR(20)     NOT NULL DEFAULT 'processing' COMMENT 'processing/ready/failed',
    mineru_batch_id     VARCHAR(100)    NULL COMMENT 'MinerU 批量任务ID',
    chunk_count         INT             NOT NULL DEFAULT 0 COMMENT '切分块数量',
    error_message       VARCHAR(1000)   NULL COMMENT '失败原因',
    created_by          VARCHAR(100)    NULL COMMENT '上传人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX ix_ai_insurance_document_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保险 PDF 知识库文档';

-- ==================== 会话 ====================
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id                  VARCHAR(32)     NOT NULL COMMENT '会话ID',
    user_id             BIGINT          NOT NULL COMMENT '所属用户ID（会话隔离）',
    product_id          BIGINT          NOT NULL COMMENT '关联保险商品ID',
    title               VARCHAR(100)    NOT NULL DEFAULT '新会话' COMMENT '会话标题（首问摘要）',
    last_checkpoint_id  VARCHAR(120)    NULL COMMENT '待恢复的 LangGraph 检查点',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_ai_session_owner (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保险咨询会话';

-- ==================== 消息 ====================
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    session_id          VARCHAR(32)     NOT NULL COMMENT '会话ID',
    role                VARCHAR(16)     NOT NULL COMMENT 'user/assistant',
    content             TEXT            NOT NULL COMMENT '消息内容',
    sources             JSON            NULL COMMENT '引用片段',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX ix_ai_chat_message_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保险咨询消息';