---
kind: external_dependency
name: MySQL 关系型数据库
slug: mysql
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### MySQL
- 角色：持久化用户、商品、订单核心数据；容器启动时自动执行 `init-sql/01-init-database.sql` 建库建表并导入演示数据。
- 镜像版本：`mysql:8.0`，字符集 `utf8mb4`，collation `utf8mb4_unicode_ci`，认证插件 `mysql_native_password`。
- 客户端约束：Docker 编排中数据库名为 `qoder_fullstack`，root/root；初始化脚本必须用 `--default-character-set=utf8mb4` 手动执行，否则中文会被 latin1 二次编码成乱码。