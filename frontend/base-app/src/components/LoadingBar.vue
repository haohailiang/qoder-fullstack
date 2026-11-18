<template>
  <transition name="loading-bar-fade">
    <div v-if="visible" class="loading-bar" role="progressbar" aria-busy="true">
      <div class="loading-bar__inner"></div>
    </div>
  </transition>
</template>

<script setup lang="ts">
/** 顶部细进度条：仅表示路由跳转/请求进行中，不承载文案，不遮挡交互 */
defineProps<{ visible: boolean }>()
</script>

<style scoped>
.loading-bar {
  position: fixed;
  top: 60px;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 2000;
  overflow: hidden;
  pointer-events: none;
}

.loading-bar__inner {
  width: 40%;
  height: 100%;
  background-color: var(--el-color-primary, #409eff);
  border-radius: 1px;
  animation: loading-bar-move 1s ease-in-out infinite;
}

@keyframes loading-bar-move {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}

.loading-bar-fade-enter-active,
.loading-bar-fade-leave-active {
  transition: opacity 0.2s ease;
}

.loading-bar-fade-enter-from,
.loading-bar-fade-leave-to {
  opacity: 0;
}
</style>
