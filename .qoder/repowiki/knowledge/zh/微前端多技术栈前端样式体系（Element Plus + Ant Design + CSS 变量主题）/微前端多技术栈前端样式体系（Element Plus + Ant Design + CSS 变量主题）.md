---
kind: frontend_style
name: 微前端多技术栈前端样式体系（Element Plus + Ant Design + CSS 变量主题）
category: frontend_style
scope:
    - '**'
source_files:
    - frontend/base-app/src/styles/index.css
    - frontend/base-app/src/utils/theme.ts
    - frontend/base-app/package.json
    - frontend/base-app/src/views/Login.vue
    - frontend/product-app/package.json
    - frontend/product-app/src/pages/ProductList.tsx
    - frontend/order-app/package.json
---

## 1. 总体方案

仓库包含三个独立的前端子应用，采用 qiankun 微前端架构：
- `frontend/base-app`：Vue 3 + TypeScript + Vite 主应用，使用 Element Plus 组件库。
- `frontend/product-app`：React 18 + TypeScript + Vite 子应用，使用 Ant Design (antd) 5。
- `frontend/order-app`：原生 JS 子应用，无 UI 组件库依赖。

每个子应用通过各自的 `package.json` 声明依赖，构建工具统一为 Vite，并通过 `vite-plugin-qiankun`（子应用）与 `qiankun`（主应用）集成。样式方面没有统一的 CSS-in-JS 或共享设计系统，各子应用各自维护自己的视觉风格。

## 2. 关键文件

- `frontend/base-app/src/styles/index.css`：全局基础样式，定义 body 字体、背景色、颜色以及暗黑模式 `html.dark` 覆盖规则。
- `frontend/base-app/src/utils/theme.ts`：主题状态管理，提供 `themeRef`、`setTheme`、`toggleTheme`、`applyTheme`、`initTheme` 等函数，将主题值写入 `localStorage('qoder_theme')` 并给 `<html>` 添加/移除 `dark` class。
- `frontend/base-app/package.json`：声明 Element Plus (`element-plus ^2.8.4`)、`@element-plus/icons-vue`、`vue-i18n`、`qiankun` 等依赖。
- `frontend/product-app/package.json`：声明 Ant Design (`antd ^5.22.0`)、`react-i18next`、`vite-plugin-qiankun` 等依赖。
- `frontend/base-app/src/views/Login.vue`：基于 Element Plus 的登录页，大量使用 `el-card`、`el-form`、`el-input`、`el-button`、`el-message` 等组件。
- `frontend/product-app/src/pages/ProductList.tsx`：基于 Ant Design 的商品列表页，直接使用 `Card`、`Input`、`Select`、`Button`、`List`、`Tag`、`Space` 等组件。

## 3. 架构与约定

### 3.1 主题系统（仅 base-app 实现）
- 主题类型定义为 `'dark' | 'light'`，默认值为 `dark`。
- 主题状态通过 Vue `ref` 暴露为响应式 `themeRef`，供组件实时读取。
- 持久化策略：优先从 `localStorage('qoder_theme')` 读取；若已登录则异步调用后端 `/user/users/theme` 同步用户偏好到数据库，失败时静默回退到本地缓存。
- 视觉切换通过在 `<html>` 根元素上添加/移除 `class="dark"` 实现，配合 `index.css` 中 `html.dark` 选择器覆盖 `background-color` 与 `color`。
- Element Plus 的暗黑模式正是通过 `<html>.dark` class 驱动，因此该方案与 Element Plus 官方暗黑模式机制一致。

### 3.2 样式组织方式
- **base-app**：全局样式集中在 `src/styles/index.css`；组件级样式使用 Vue SFC 的 `<style scoped>` 块（如 Login.vue 中的 `.auth-page`、`.auth-card`、`.captcha-row` 等类名），未引入 SCSS/Less。
- **product-app**：几乎全部样式以内联 `style={{}}` 形式写在 JSX 中（例如 `minHeight: '100vh'`、`padding: 24`、`background: pageBg`），未见独立的 CSS/SCSS 文件。
- **order-app**：无样式代码（仅有 `app.ts`、`main.ts` 等入口文件）。

### 3.3 设计令牌（Design Tokens）
- 未建立集中化的设计令牌文件（无 `tokens.*`、`variables.scss`、`tailwind.config.*` 等）。
- 颜色与间距主要直接写死在 CSS/JS 中：
  - base-app 全局背景 `#f5f7fa`、暗黑背景 `#141414`、文字色 `#303133` / `#e5eaf3`。
  - product-app 根据 `qoder_theme` 计算 `pageBg = isDark ? '#141414' : '#f5f5f5'`。
- 部分颜色通过 CSS 变量引用组件库内置 token，如 `var(--el-border-color, #dcdfe6)`、`var(--el-text-color-secondary, #909399)`、`var(--ant-color-fill-tertiary, #f0f2f5)`、`var(--ant-color-error, #f5222d)` 等，以便跟随组件库主题。

### 3.4 国际化与样式的关系
- base-app 使用 `vue-i18n`，product-app 使用 `react-i18next`，文案走 i18n，但样式本身不随语言变化。

### 3.5 响应式策略
- 未发现媒体查询或移动端适配逻辑。
- product-app 的 Ant Design `List.grid` 配置了断点 `xs/sm/md/lg/xl/xxl`，属于组件库自带的栅格响应式能力。

## 4. 约定与约束

- **基座与子应用分离**：base-app 作为 qiankun 主应用加载 product-app 与 order-app 两个子应用，三者各自拥有独立的 `package.json`、`vite.config.ts` 和构建产物。
- **组件库选择按框架划分**：Vue 侧使用 Element Plus，React 侧使用 Ant Design，不存在跨框架共享的 UI 组件包。
- **暗黑模式开关位置**：主题切换逻辑集中在 `base-app/src/utils/theme.ts`，通过操作 `<html>` 元素的 `dark` class 生效；product-app 通过直接读取 `localStorage('qoder_theme')` 自行判断暗色背景，未复用 base-app 的 theme 模块。
- **CSS 命名**：base-app 使用 BEM 风格的简单类名（如 `.auth-page`、`.auth-card`、`.captcha-row`、`.sms-row`），product-app 主要使用内联 style 属性而非类名。
- **无 Tailwind/SCSS**：仓库中未发现 `tailwind.config.*`、`postcss.config.*`、`*.scss` 或 `*.less` 文件，样式均为原生 CSS 或内联 style。
- **无统一设计系统**：颜色、字号、间距等视觉参数在各文件中硬编码，未在共享层做抽象。

## 5. 结论

该仓库的前端样式体系是“多技术栈、多组件库、弱共享”的模式：base-app 基于 Element Plus 并提供一套完整的暗黑主题切换机制，product-app 基于 Ant Design 且样式以内联为主，order-app 无样式。整体缺乏跨应用的统一设计令牌、CSS 方法论或主题共享层，视觉一致性依赖于各子应用自行维护。