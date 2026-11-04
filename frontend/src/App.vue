<script setup lang="ts"></script>

<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <span class="app-title">全栈示例 · 用户管理</span>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  background-color: #409eff;
  color: #fff;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
}
</style>
