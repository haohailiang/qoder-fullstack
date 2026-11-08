---
kind: build_system
name: 基于 Maven + Docker Compose 的全栈构建与编排体系
category: build_system
scope:
    - '**'
source_files:
    - backend/pom.xml
    - backend/Dockerfile.user
    - backend/Dockerfile.gateway
    - backend/Dockerfile.product
    - backend/Dockerfile.order
    - frontend/base-app/package.json
    - frontend/base-app/Dockerfile
    - docker-compose.yml
    - docker-compose-infra.yml
    - vm-ssh.js
    - init-sql/01-init-database.sql
---

## 1. 整体方案

仓库采用 **多语言、分层式** 的构建与部署方式：
- 后端（Spring Boot 微服务）使用 **Maven** 作为构建工具，通过 `backend/pom.xml` 父 POM 聚合 `ecommerce-common / user / gateway / product / order` 五个模块。
- 前端（Vue3 + React + 原生 JS 子应用）使用 **Vite** 构建，每个子应用独立 `package.json`。
- 统一编排通过根级 `docker-compose.yml` 拉起基础设施（MySQL/Redis/Nacos/RabbitMQ/Elasticsearch）+ 所有后端服务 + 三个前端 Nginx 容器。
- 本地 VM 部署辅助脚本 `vm-ssh.js` 基于 `ssh2` 提供 SSH 命令执行与 SFTP 上传能力。

没有发现 CI/CD（GitHub Actions、Jenkinsfile 等）或 Makefile，因此本仓库的“发布流水线”以 `docker-compose up --build` 为核心入口。

## 2. 关键文件

| 路径 | 作用 |
|---|---|
| `backend/pom.xml` | Maven 父工程，声明 Spring Boot 3.3.5、Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.1.2，并通过 `<dependencyManagement>` 集中管理 BOM 与内部公共模块版本 |
| `backend/Dockerfile.{user,gateway,product,order}` | 每个后端服务的多阶段 Docker 镜像定义 |
| `frontend/base-app/package.json` | Vue 主应用的 Vite 构建脚本（`dev` / `build` / `preview`） |
| `frontend/base-app/Dockerfile` | 前端基础镜像：Node 20 构建 → Nginx 静态托管 |
| `docker-compose.yml` | 全栈服务编排（基础设施 + 4 个后端 + 3 个前端） |
| `docker-compose-infra.yml` | 仅基础设施（与主 compose 分离） |
| `vm-ssh.js` | Node.js SSH/SFTP 部署辅助脚本 |
| `init-sql/01-init-database.sql` | MySQL 初始化数据，由 compose 挂载到 `/docker-entrypoint-initdb.d` |

## 3. 架构与约定

### 3.1 后端 Maven 构建
- 父 POM `ecommerce-parent` 继承 `spring-boot-starter-parent:3.3.5`，Java 版本固定为 `17`。
- 通过 `spring-cloud-dependencies` 与 `spring-cloud-alibaba-dependencies` 两个 BOM 统一管理依赖版本，子模块无需再声明版本号。
- 内部公共模块 `ecommerce-common` 在父 POM 中声明版本为 `${project.version}`，保证各服务引用同一内部包。
- 每个 `Dockerfile.*` 采用 **双阶段构建**：
  - builder 阶段：`maven:3.9-eclipse-temurin-17`，先 `mvn dependency:go-offline -pl <module> -am -B` 预拉取依赖，再 `mvn package -pl <module> -am -DskipTests -B` 打包。
  - runtime 阶段：`eclipse-temurin:17-jre-alpine`，仅拷贝最终 jar 并以 `java -jar app.jar` 启动。
- 每个服务 Dockerfile 显式 COPY 全部子模块的 `pom.xml`，确保依赖缓存命中；只 COPY 目标模块源码，减少构建上下文。

### 3.2 前端构建
- 每个子应用独立 `package.json`，使用 Vite + TypeScript（base-app 额外使用 `vue-tsc` 做类型检查）。
- 构建产物输出到 `dist/`，由各自 `Dockerfile` 中的 `node:20-alpine` 构建后，用 `nginx:alpine` 静态托管。
- base-app 的 `build` 脚本顺序为 `vue-tsc --noEmit && vite build`，即先类型检查再编译。

### 3.3 容器编排
- `docker-compose.yml` 将基础设施与应用放在同一文件中，通过 `depends_on` + `condition` 控制启动顺序：
  - MySQL 使用 `healthcheck`（`mysqladmin ping`），其他服务依赖其 `service_healthy`。
  - Redis/Nacos/RabbitMQ/Elasticsearch 使用 `service_started`。
- 后端服务通过环境变量注入配置：`SPRING_PROFILES_ACTIVE=docker`、`SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=nacos:8848`、`SPRING_DATA_REDIS_HOST=redis` 等，覆盖 `application.yml` 中的默认值。
- 前端三个子应用分别暴露端口 `80`（base）、`7100`（product）、`7200`（order）。

### 3.4 VM 部署辅助
- `vm-ssh.js` 硬编码了 VM 地址 `192.168.178.100`、用户名 `root`、密码 `root123456`。
- 支持两种模式：`node vm-ssh.js <command>` 远程执行命令，`node vm-ssh.js upload <local> <remote>` 通过 SFTP 上传文件。
- 该脚本是仓库内唯一的“部署到远端主机”的能力，未集成到 docker-compose 流程中。

## 4. 约定与约束

- **Java 版本**：父 POM 固定 `java.version=17`，所有 Dockerfile 使用 `eclipse-temurin:17-jre-alpine` 运行，构建镜像使用 `maven:3.9-eclipse-temurin-17`。这是由镜像标签强制约束的。
- **测试跳过**：所有后端 Dockerfile 在 `mvn package` 时均带 `-DskipTests`，即生产镜像不包含单元测试执行。
- **依赖缓存策略**：先 COPY 所有 `pom.xml` 并执行 `dependency:go-offline`，再 COPY 源码，利用 Maven 离线模式加速后续构建。
- **Nacos 配置**：所有后端服务通过 `SPRING_PROFILES_ACTIVE=docker` 激活 docker profile，并在 compose 中注入 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` 指向容器名 `nacos`。
- **MySQL 字符集**：compose 中显式设置 `LANG: C.UTF-8`、`LC_ALL: C.UTF-8` 以及 `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`，以避免官方镜像默认 locale 导致的中文乱码（注释中有明确说明）。
- **前端构建产物**：每个子应用独立构建并独立 Nginx 容器，不存在统一的 CDN 或反向代理层（base-app 的 nginx.conf 仅用于静态资源分发）。
- **无 CI/CD**：仓库未发现 GitHub Actions、Jenkinsfile、GitLab CI 等持续集成配置，也没有 Makefile 或 shell 构建脚本（除 `vm-ssh.js`）。本地构建入口为 `docker-compose up --build`。
- **版本管理**：后端使用 Maven 快照版本 `1.0.0-SNAPSHOT`；前端 base-app 版本写死为 `0.1.0`，其余子应用未在顶层 package.json 中声明统一版本策略。