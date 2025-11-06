<template>
  <transition name="fade">
    <div v-if="visible" class="overlay" @click.self="close">
      <div class="ppt-popup" :style="popupStyle">
        <div class="ppt-content">
          <!-- 左右点击区域 -->
          <div class="click-zone left" @click.stop="prevSlide"></div>
          <img
            :src="images[currentIndex]"
            class="ppt-image"
            alt="slide"
            @mousedown.stop="startDrag"
          />
          <div class="click-zone right" @click.stop="nextSlide"></div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue';

const visible = ref(false);
const images = ref([]);
const currentIndex = ref(0);

// 拖拽状态
const pos = reactive({ x: window.innerWidth / 2, y: window.innerHeight / 2 });
const dragging = ref(false);
let offset = { x: 0, y: 0 };

// 读取PPT图片
const pptFolder = '/ppt/';
const possibleExtensions = ['jpg', 'png','PNG'];
const maxSlides = 20;

onMounted(() => {
  const loaded = [];
  for (let i = 1; i <= maxSlides; i++) {
    for (const ext of possibleExtensions) {
      const img = new Image();
      img.src = `${pptFolder}${i}.${ext}`;
      img.onload = () => {
        if (!loaded.includes(img.src)) {
          loaded.push(img.src);
          loaded.sort((a, b) => a.localeCompare(b));
          images.value = [...loaded];
        }
      };
    }
  }
});

// 打开/关闭
function open() {
  visible.value = true;
  // currentIndex.value = 0;
}
function close() {
  visible.value = false;
}

// 翻页
function prevSlide() {
  if (currentIndex.value > 0) currentIndex.value--;
}
function nextSlide() {
  if (currentIndex.value < images.value.length - 1) currentIndex.value++;
}

// 拖拽
function startDrag(e) {
  dragging.value = true;
  offset = {
    x: e.clientX - pos.x,
    y: e.clientY - pos.y
  };
  window.addEventListener('mousemove', onDrag);
  window.addEventListener('mouseup', stopDrag);
}
function onDrag(e) {
  if (!dragging.value) return;
  pos.x = e.clientX - offset.x;
  pos.y = e.clientY - offset.y;
}
function stopDrag() {
  dragging.value = false;
  window.removeEventListener('mousemove', onDrag);
  window.removeEventListener('mouseup', stopDrag);
}

// 计算弹窗样式
const popupStyle = computed(() => ({
  position: 'fixed',
  top: `${pos.y}px`,
  left: `${pos.x}px`,
  transform: 'translate(-50%, -50%)', // 居中
  cursor: dragging.value ? 'grabbing' : 'default',
  zIndex: 9999,
}));

defineExpose({ open, close });
</script>

<style scoped>


.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* 全屏遮罩 */
.overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4); /* 半透明暗色 */
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9998;
}

/* 弹窗本身 */
.ppt-popup {
  background: transparent;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: grab;
  z-index: 9999;
}

/* PPT内容 */
.ppt-content {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 幻灯片图片 */
.ppt-image {
  max-width: 110vw;
  max-height: 110vh;
  user-select: none;
  pointer-events: all;
}

/* 左右点击区域 */
.click-zone {
  position: absolute;
  top: 0;
  width: 50%;
  height: 100%;
  z-index: 2;
}
.click-zone.left {
  left: 0;
}
.click-zone.right {
  right: 0;
}

</style>