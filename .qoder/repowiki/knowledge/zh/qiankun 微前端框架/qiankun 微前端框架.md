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
- 加载态：`src/micro/index.ts` 用 `subAppState.pending` 集合 + `subAppLoading` 计算属性跟踪加载，`beforeLoad`/`beforeMount` 入队、`afterMount`/`afterUnmount` 出队，`App.vue` 再经 `useDelayedBusy` 延迟后展示 `SubAppPlaceholder`，避免闪现。
- history 保护：`start()` 前调用 `protectHistoryState()` 包装 `history.pushState/replaceState`，合并 `{current, back, forward, position}` 状态，修正 qiankun 与子路由刷新时的 state 丢失。
- 鉴权与分流：子应用不自带登录逻辑，拿主应用的 `getToken`；主应用根据 `resolveHomePath()` 决定 ADMIN 进 `/users`、USER 进 `/product`，子路由受 `meta.adminOnly` 守卫。