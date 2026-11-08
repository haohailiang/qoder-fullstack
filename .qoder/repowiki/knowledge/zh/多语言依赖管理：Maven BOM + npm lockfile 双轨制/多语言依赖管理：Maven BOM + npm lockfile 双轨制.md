---
kind: dependency_management
name: 多语言依赖管理：Maven BOM + npm lockfile 双轨制
category: dependency_management
scope:
    - '**'
source_files:
    - backend/pom.xml
    - backend/.mvn/wrapper/maven-wrapper.properties
    - backend/ecommerce-common/pom.xml
    - backend/ecommerce-user/pom.xml
    - package.json
    - package-lock.json
    - frontend/base-app/package.json
    - frontend/product-app/package.json
    - frontend/order-app/package.json
---

## 1. 使用的系统/工具

仓库采用**双轨依赖管理**：
- 后端 Java 微服务使用 **Maven**（Spring Boot 3.3.5，通过 `spring-boot-starter-parent` 继承），配合 Maven Wrapper（`.mvn/wrapper/maven-wrapper.properties` 锁定 maven 3.8.8、wrapper 3.3.4）。
- 前端微前端应用使用 **npm**（Node.js），每个子应用独立 `package.json` + `package-lock.json`。
- 根目录仅有一个极小的 `package.json`（仅含 `ssh2` devDependency，供 `vm-ssh.js` 部署脚本使用）。

未发现私有仓库配置（无 `.m2/settings.xml`、无 npm `.npmrc`、无 GOPRIVATE 等）。所有第三方包均从公共仓库拉取。

## 2. 关键文件

- `backend/pom.xml` — 父 POM，集中声明 Spring Cloud / Spring Cloud Alibaba BOM、内部模块与版本属性。
- `backend/ecommerce-common/pom.xml`、`backend/ecommerce-user/pom.xml`、`backend/ecommerce-gateway/pom.xml`、`backend/ecommerce-product/pom.xml`、`backend/ecommerce-order/pom.xml` — 各子模块 POM，仅声明自身依赖，不写版本号。
- `backend/.mvn/wrapper/maven-wrapper.properties` — 固定 Maven 发行版与 wrapper jar 地址。
- `frontend/base-app/package.json`、`frontend/product-app/package.json`、`frontend/order-app/package.json` — 三个 qiankun 子应用的依赖声明。
- `package.json` + `package-lock.json` — 根级 npm 依赖（仅 `ssh2`）及锁文件。

## 3. 架构与约定

### 后端（Maven）
- 采用**聚合工程 + 父 POM 管理**模式：根 `pom.xml` 的 `<modules>` 列出 `ecommerce-common`、`ecommerce-user`、`ecommerce-gateway`、`ecommerce-product`、`ecommerce-order` 五个子模块。
- 通过 `<dependencyManagement>` 引入两个 BOM：
  - `org.springframework.cloud:spring-cloud-dependencies:${spring-cloud.version}`（2023.0.3）
  - `com.alibaba.cloud:spring-cloud-alibaba-dependencies:${spring-cloud-alibaba.version}`（2023.0.1.2）
- 公共框架版本集中在父 POM 的 `<properties>` 中统一声明：`java.version=17`、`spring-cloud.version`、`spring-cloud-alibaba.version`、`jjwt.version=0.11.5`、`springdoc.version=2.6.0`。子模块引用这些属性而不硬编码版本。
- 内部模块 `ecommerce-common` 在父 POM 的 `dependencyManagement` 中以 `${project.version}` 引用，保证子模块无需指定版本号即可依赖它。
- Lombok 在所有子模块中标记为 `<optional>true</optional>`，并在 `spring-boot-maven-plugin` 的 `<excludes>` 中排除打包。
- 数据库驱动（`mysql-connector-j`）、Redis、Nacos Discovery/Config、MyBatis Starter、Springdoc OpenAPI 等依赖在各业务模块 POM 中按需声明。

### 前端（npm）
- 每个 qiankun 子应用（`base-app` Vue3 + Element Plus、`product-app` React + Ant Design、`order-app` 原生 JS）各自维护独立的 `package.json`，互不共享依赖。
- 所有依赖使用语义化版本范围（`^` 或 `~`），例如 `vue: ^3.5.12`、`vite: ^5.4.9`、`typescript: ~5.6.3`。
- 每个子应用都生成并提交对应的 `package-lock.json`（lockfileVersion 3），用于冻结安装树。
- 根 `package.json` 仅包含部署辅助工具 `ssh2`，不参与业务构建。

## 4. 约定与约束

- **Java 依赖版本必须通过父 POM 的 `<properties>` 或 `<dependencyManagement>` 统一管理**：子模块 POM 中未出现任何硬编码的 Spring / Spring Cloud / Spring Cloud Alibaba 版本号，全部由父 POM 的 BOM 和属性提供（依据各子模块 POM 的实际内容）。
- **内部模块 `ecommerce-common` 的版本与父工程一致**：通过 `${project.version}` 引用，禁止子模块单独指定其版本（依据父 POM `dependencyManagement` 中的声明）。
- **Lombok 不参与运行时打包**：在所有子模块 POM 中声明为 `<optional>true</optional>`，且 `spring-boot-maven-plugin` 显式 exclude（依据 `ecommerce-user/pom.xml` 的 build 配置，其他模块遵循相同模式）。
- **Maven 工具链不可随意升级**：通过 Maven Wrapper 锁定 `apache-maven 3.8.8` 与 `maven-wrapper 3.3.4`，下载地址指向 Maven Central（依据 `.mvn/wrapper/maven-wrapper.properties`）。
- **前端依赖变更需同步更新 lockfile**：每个子应用均有 `package-lock.json`，表明团队以 npm 锁文件作为可复现安装的契约。
- **未发现私有仓库或代理配置**：仓库内没有 `.m2/settings.xml`、`.npmrc`、`settings.gradle` 等私有源配置文件，所有依赖均来自公共仓库（Maven Central、npm registry）。
- **未发现 vendoring 策略**：Java 依赖通过 Maven 远程解析，前端依赖通过 node_modules 安装，无 vendor 目录或源码级 vendoring。