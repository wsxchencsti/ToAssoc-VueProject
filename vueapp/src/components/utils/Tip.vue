<template>
  <div class="info-icon-wrapper" @mouseenter="show = true" @mouseleave="show = false">
    <img src="/icons/common/info.svg" class="info-icon" />
    <transition name="tooltip-fade">
      <div v-if="show" class="tooltip">{{ text }}</div>
    </transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const props = defineProps({
  text: { type: String, default: '提示信息' }
})
const show = ref(false)
</script>

<style scoped>
.info-icon-wrapper {
  position: relative;
  display: inline-block;
}

.info-icon {
  width: 16px;
  height: 16px;
  cursor: pointer;
  vertical-align: middle;
  margin-left: -5px;
  transition: opacity 0.2s ease;
}

.info-icon:hover {
  opacity: 0.8;
}

.tooltip {
  font-family: '幼圆';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-top: 8px;
  padding: 8px 12px;
  background-color: white;
  color: #666;
  /* 灰色文字 */
  font-size: 12px;
  border-radius: 6px;
  /* 移除边框，添加阴影 */
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15),
    0 1px 4px rgba(0, 0, 0, 0.1);
  /* 多层阴影更立体 */
  white-space: nowrap;
  pointer-events: none;
  z-index: 1000;
  line-height: 1.4;
}

/* 添加小箭头指向图标 */
.tooltip::before {
  content: '';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-bottom-color: white;
  /* 箭头颜色与背景一致 */
  filter: drop-shadow(0 -1px 2px rgba(0, 0, 0, 0.1));
  /* 给箭头也加阴影 */
}

/* 淡入淡出动画 */
.tooltip-fade-enter-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.tooltip-fade-leave-active {
  transition: all 0.15s cubic-bezier(0.4, 0, 1, 1);
}

.tooltip-fade-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
  scale: 0.95;
}

.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
  scale: 0.95;
}

.tooltip-fade-enter-to {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
  scale: 1;
}
</style>