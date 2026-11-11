-- ============================================================================
-- RBAC 迁移脚本（对已存在的 qoder_fullstack 库执行一次即可）
--
-- 背景：01-init-database.sql 使用 CREATE TABLE IF NOT EXISTS，已建好的库不会自动获得新列，
-- 而 UserMapper 的查询/写入已包含 role 列，缺列会导致用户服务 SQL 报错。
--
-- 执行方式（中间件部署在 192.168.178.100）：
--   mysql -h 192.168.178.100 -uroot -p qoder_fullstack < backend/ecommerce-user/src/main/resources/db/migration-add-users-role.sql
-- ============================================================================

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN' AFTER age;

-- 预置管理员账号拥有管理接口权限
UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
