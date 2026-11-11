-- 强制本次会话按 UTF-8 解释脚本内容，避免客户端按 latin1 处理本文件
-- 导致中文注释/数据出现双重编码乱码。
SET NAMES utf8mb4;

-- 用户表：承载注册、登录、用户 CRUD 所需字段，以及 createdBy / createdDate /
-- lastModifiedBy / lastModifiedDate 四个审计字段（下划线命名，Java 侧通过
-- MyBatis UserMapper.xml 的 resultMap 映射为驼峰命名的实体字段）。
--
-- 本地开发首次使用前，请先创建数据库再执行本脚本：
--   CREATE DATABASE IF NOT EXISTS qoder_fullstack DEFAULT CHARSET utf8mb4;
--   USE qoder_fullstack;
--   source backend/src/main/resources/db/schema.sql
-- 或在 Navicat / DBeaver 等客户端中直接执行本文件内容。

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)  DEFAULT NULL COMMENT '登录认证标识（注册时必填，旧 CRUD 数据可为空）',
    password      VARCHAR(100) DEFAULT NULL COMMENT 'BCrypt 加密后的密码，不对外返回',
    name          VARCHAR(64)  NOT NULL COMMENT '姓名',
    email         VARCHAR(128) NOT NULL COMMENT '邮箱',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号，用于接收短信验证码',
    age           INT          DEFAULT NULL COMMENT '年龄',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN，网关注入 JWT role claim 后由 @PreAuthorize 校验',
    created_by         VARCHAR(64) DEFAULT NULL COMMENT '创建人（当前登录用户标识，未登录/系统操作记为 system）',
    created_date       DATETIME    DEFAULT NULL COMMENT '创建时间（服务端时间）',
    last_modified_by   VARCHAR(64) DEFAULT NULL COMMENT '最后修改人',
    last_modified_date DATETIME    DEFAULT NULL COMMENT '最后修改时间（服务端时间）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';
