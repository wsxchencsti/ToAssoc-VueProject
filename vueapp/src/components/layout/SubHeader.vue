<template>
  <div class="sub-header">
    <transition name="fade-line" mode="out-in" appear>
      <div v-if="!loading" class="line" :key="query"></div>
    </transition>

    <div class="left">
      <transition name="slide-left" mode="out-in" appear>
        <div v-if="!loading" class="query-info" :key="query">
          <p>「{{ query }}」</p>
        </div>
      </transition>
    </div>

    <div class="center">
      <div v-if="!loading" class="line-text-wrapper">
        <transition name="slide-left-delayed" mode="out-in" appear>
          <div class="line-text" :key="query">Current Topic</div>
        </transition>
      </div>
    </div>

    <!-- 把 right 部分移回 sub-header 内部 -->
    <div class="right">
      <div class="controls">
        <button class="control-btn filter-btn" :class="{ active: filterActive, disabled: activeBtns.spark }"
          :disabled="loading || activeBtns.spark" @click="toggleButton('filter')">
          <img :src="filterActive ? '/icons/subheader/filter_active.svg' : '/icons/subheader/filter_inactive.svg'"
            class="btn-icon" />
          Filter
        </button>
        <button class="control-btn spark-btn" :class="{ active: activeBtns.spark }" :disabled="loading"
          @click="toggleButton('spark')">
          <img :src="activeBtns.spark ? '/icons/subheader/flower_active.svg' : '/icons/subheader/flower_inactive.svg'"
            class="btn-icon" />
          SparkClustering
        </button>
        <button class="control-btn construc-btn" :class="{ active: activeBtns.debug }" :disabled="loading"
          @click="toggleButton('debug')"><img
            :src="activeBtns.debug ? '/icons/subheader/construction_active.svg' : '/icons/subheader/construction_inactive.svg'"
            class="btn-icon" />Debug</button>
      </div>
    </div>
  </div>
  <ConfirmPopup :visible="showSparkConfirm" @confirm="handleSparkConfirm" @cancel="handleSparkCancel" />
  <div v-if="isProcessing" class="spark-loading-overlay">
    <div class="spinner"></div>
    <p>SparkClustering...</p>
  </div>
</template>

<script setup>
import { reactive, ref, watch, inject } from 'vue'
import ConfirmPopup from '../utils/ConfirmPopup.vue';

const props = defineProps({
  query: { type: String, default: '' },
  loading: Boolean,
  filterActive: { type: Boolean, default: false },
  debugMode: { type: Boolean, default: false }
});
const debugMode = inject('debugMode')
if (debugMode.value)
  console.log('filterActive:', props.filterActive);

const emit = defineEmits(['toggle-filter', 'spark-toggle', 'debug-toggle'])
// 监听 prop 变化
watch(() => props.filterActive, (newVal) => {
  if (debugMode.value)
    console.log('SubHeader - filterActive 变化:', newVal);
});

// 按钮独立状态
const activeBtns = reactive({
  filter: false,
  spark: false,
  debug: false,
  btn4: false
})

// SparkClustering 确认弹窗显示状态
const showSparkConfirm = ref(false)
const isProcessing = ref(false)

// 监听 query 变化（当有新搜索时）
watch(() => props.query, (newQuery, oldQuery) => {
  if (debugMode.value)
    console.log('Query 变化:', { oldQuery, newQuery });
  if (newQuery && newQuery !== oldQuery) {
    resetAllButtons();
  }
});

// 重置所有按钮状态的方法
const resetAllButtons = () => {
  Object.keys(activeBtns).forEach(key => {
    activeBtns[key] = false;
  });
  showSparkConfirm.value = false; // 同时关闭确认弹窗
  if (debugMode.value)
    console.log('所有按钮已重置为 inactive 状态');
};


function toggleButton(name) {
  if (name === 'filter') {
    if (activeBtns.spark) {
      if (debugMode.value)
        console.log('SparkClustering 已激活，无法操作 Filter');
      return;
    }
    // Filter 按钮直接触发父组件事件，不维护本地状态
    emit('toggle-filter')
  } else if (name === 'spark') {
    // SparkClustering 按钮需要确认
    handleSparkClustering()
  } else if (name === 'debug') {
    // 直接切换状态并发出事件
    activeBtns.debug = !activeBtns.debug;
    emit('debug-toggle', activeBtns.debug)

    // 改进的日志输出
    console.log(`----------- DebugMode ${activeBtns.debug ? 'On' : 'Off'} -----------`);
  }
}

const handleSparkClustering = () => {
  // 如果已经是激活状态，直接关闭
  if (activeBtns.spark) {
    activeBtns.spark = false;
    emit('spark-toggle', false)
    return;
  }
  // 如果是非激活状态，显示确认弹窗
  showSparkConfirm.value = true;
}

// 确认启用 SparkClustering
const handleSparkConfirm = async () => {
  isProcessing.value = true;
  showSparkConfirm.value = false;
  // ✅ 新增：如果 Filter 是打开状态，就收起它
  if (props.filterActive) {
    if (debugMode.value)
      console.log('检测到 Filter 已打开，自动收起');
    emit('toggle-filter'); // 这会触发父组件的 handleToggleFilter
  }

  // 添加2秒延时，模拟处理过程
  await new Promise(resolve => setTimeout(resolve, 100));
  isProcessing.value = false;
  activeBtns.spark = true;
  if (debugMode.value)
    console.log('SparkClustering 已启用');
  emit('spark-toggle', true)
}

// 取消启用 SparkClustering
const handleSparkCancel = () => {
  showSparkConfirm.value = false;
  if (debugMode.value)
    console.log('取消SparkClustering启用');
}

</script>

<style scoped>
.sub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: fixed;
  top: 50px;
  left: 0;
  width: 100%;
  height: 45px;
  background-color: #fefefe;
  box-shadow: 0 2px 7px rgba(0, 0, 0, 0.3);
  padding: 0 20px;
  padding-top: 2px;
  z-index: 1500;
}

.query-info,
.controls {
  position: relative;
  /* 提升层级 */
  z-index: 1;
}

.left,
.center,
.right {
  display: flex;
  align-items: center;
}

.left {
  min-width: 0;
  /* 支持 text-overflow */
}

.query-info {
  z-index: 10;
  display: flex;
  align-items: center;
  height: 100%;
  padding-left: 25px;
}

.query-info p {
  font-family: 'Didot', 'Bodoni Moda', serif;
  font-synthesis: style;
  font-style: italic;
  margin: 0;
  padding: 0;
  font-weight: 500;
  transform: skew(-10deg) scaleY(1.8);
  /* 负值向右倾斜，正值向左倾斜 */
  display: inline-block;
  background-color: #fff;
  max-width: 900px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.center {
  flex: 1;
  /* 占满剩余空间 */
  justify-content: flex-start;
  position: relative;
}

.controls {
  z-index: 5;
  display: flex;
  gap: 10px;
  padding-right: 20px;
}

.line-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: #2a6d88;
  background-color: #fefefe;
  padding: 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
  /* 最大右移限制 */
}

/* 固定长度线条 */
.line {
  position: absolute;
  top: 55%;
  left: 0;
  width: 80%;
  /* 固定长度 */
  height: 2px;
  background: linear-gradient(to right, #ccc 0%, #ccc 70%, rgba(204, 204, 204, 0) 100%);
  transform: translateY(-50%);
  z-index: 0;
}

.right {
  gap: 10px;
  margin-right: 20px;
}

/* === 通用按钮样式 === */
.control-btn {
  background-color: #ffffff;
  border: 1px solid #ccc;
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.25s ease;
  color: #333;
  -webkit-tap-highlight-color: transparent;
  -webkit-appearance: none;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.filter-btn,
.spark-btn,
.construc-btn {
  display: flex;
  align-items: center;
  gap: 1px;
  /* 图标和文字间距 */
  justify-content: center;
}

/* 正确的写法 */
.filter-btn .btn-icon,
.construc-btn .btn-icon,
.spark-btn .btn-icon {
  height: 1.5em;
  width: auto;
}

/* 去掉默认黑框 */
.control-btn:focus {
  outline: none;
}

.control-btn:focus-visible {
  box-shadow: 0 0 0 3px rgba(42, 136, 120, 0.2);
}

/* 悬停状态 */
.control-btn:hover {
  border-color: #2a8878;
  color: #2a8878;
  background-color: #f8fefc;
}

/* 激活状态 */
.control-btn.active {
  background-color: #2a8878;
  color: #fff;
  border-color: #2a8878;
  box-shadow: 0 3px 10px rgba(42, 136, 120, 0.25) inset,
    0 2px 5px rgba(0, 0, 0, 0.08);

}

.filter-btn.active {
  background-color: #2a8878;
  color: #fff;
  border-color: #2a8878;
  box-shadow: 0 0 10px rgba(42, 136, 120, 0.6),
    0 0 20px rgba(42, 136, 120, 0.4);
  animation: softGlow 0.5s ease-in-out infinite alternate;
  transition: all 0.1s ease;

}

.spark-btn.active {
  position: relative;
  background-color: #fff;
  color: #000;
  border: 1px solid transparent;
  border-radius: 6px;
  overflow: hidden;

  transition: all 0.3s ease;
}

/* 用伪元素绘制环绕边框 */
.spark-btn.active::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: 6px;
  padding: 2px;
  background: linear-gradient(90deg,
      rgb(136, 0, 0),
      rgb(127, 83, 0),
      rgb(131, 131, 0),
      rgb(0, 136, 0),
      rgb(0, 137, 137),
      rgb(0, 0, 127),
      rgb(123, 0, 123),
      rgb(163, 0, 0));
  background-size: 300% 300%;
  /* 为流动留空间 */
  animation: borderFlow 3s linear infinite;
  mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  mask-composite: exclude;
  -webkit-mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
}

/* 彩虹沿边框平滑流动 */
@keyframes borderFlow {
  0% {
    background-position: 0% 50%;
  }

  100% {
    background-position: 300% 50%;
  }
}







/* 呼吸发光动画 */
@keyframes softGlow {
  0% {
    box-shadow: 0 0 8px rgba(42, 136, 120, 0.3),
      0 0 16px rgba(42, 136, 120, 0.15);
  }

  100% {
    box-shadow: 0 0 16px rgba(42, 136, 120, 0.6),
      0 0 32px rgba(42, 136, 120, 0.35);
  }
}

.control-btn.active:hover {
  background-color: #319e8c;
}

.spark-btn.active:hover {
  border-color: #2a8878;
  background-color: white;
}


.line-text {
  font-family: '方正姚体', 'Times New Roman', 'Courier New';
  /* position: absolute; */
  /* top: 51.3%; */
  /* left: calc(var(--query-width, 120px) + 20px); */
  /* 左侧宽度 + 间距 */
  max-left: calc(80% - 60px);
  /* 最大右移，不超出线的右端 */
  /* transform: translate(0, -50%); */
  background-color: #fefefe;
  padding: 0 4px;
  font-size: 0.9rem;
  font-weight: 500;
  z-index: 2;
  /* color: #808080; */
  font-synthesis: style;
  font-style: italic;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;

}

/* === 线条淡入 === */
.fade-line-enter-active {
  transition: all 1.8s ease;
}

.fade-line-enter-from {
  opacity: 0;
  width: 0;
}

.fade-line-enter-to {
  opacity: 1;
  width: 80%;
}

/* === Query 滑入 === */
.slide-left-enter-active {
  transition: all 0.6s ease 0.1s;
  /* 延迟0.1秒 */
}

.slide-left-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-left-enter-to {
  opacity: 1;
  transform: translateX(0);
}

/* === Current Topic 滑入（带更多延迟） === */
.slide-left-delayed-enter-active {
  transition: all 0.6s ease 0.3s;
  /* 延迟0.3秒 */
}

.slide-left-delayed-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-left-delayed-enter-to {
  opacity: 1;
  transform: translateX(0);
}

.line-text-wrapper {
  position: absolute;
  top: 51.3%;
  left: calc(var(--query-width, 120px) + 20px);
  transform: translateY(-50%);
  max-width: calc(80% - 60px);
}

/* 动画只影响水平滑动，不影响纵向位移 */
.slide-left-delayed-enter-active {
  transition: transform 0.6s ease 0.3s, opacity 0.6s ease 0.3s;
}

.slide-left-delayed-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-left-delayed-enter-to {
  opacity: 1;
  transform: translateX(0);
}

/* 复用主加载样式，但调整 z-index 和内容 */
.spark-loading-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  z-index: 1300;
  /* 比主加载更高一层 */
  pointer-events: auto;
  backdrop-filter: blur(2px);
  /* 添加毛玻璃效果 */
}

.spinner {
  border-top-color: #ff6b6b;
}
</style>
