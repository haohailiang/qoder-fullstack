---
kind: external_dependency
name: qiankun 微前端框架
slug: qiankun
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### qiankun
- 角色：主应用 base-app 通过 qiankun 注册并加载 product-app 与 order-app 两个子应用，路由 `/product/**` 和 `/order/**` 自动加载对应子应用。
- 注意：子应用独立运行时端口分别为 7100（React）与 7200（原生 TS），开发时需先启动子应用再启动主应用。