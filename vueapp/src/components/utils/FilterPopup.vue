<template>
  <transition name="filter-fade">
    <div v-if="!filterCollapsed" class="filter-popup" :style="popupStyle" @mousedown="startDrag">
      <div class="filter-popup-header">
        <h3 class="menu-title">筛选器栏</h3>
        <button class="close-btn" @click="handleClose">
          <img src="/icons/common/close.svg" alt="close">
        </button>
      </div>
      <div class="filter-popup-content" @mousedown.stop>
        <h4 class="filter-title">Top-K数量</h4>
        <div class="slider-container">
          <vue-slider v-model="sliderValue" :min="1" :max="totalPapers" :dot-size="14" :width="'70%'" :lazy="false"
            :tooltip="'always'" :process-style="{ backgroundColor: '#2a8878' }"
            :rail-style="{ backgroundColor: '#e0e0e0' }" :drag-on-click="false" @input="handleSliderChange" />
        </div>

        <h4 class="filter-title">发表年份</h4>
        <div class="year-container">
          <vue-slider v-model="yearRange" :data="availableYears" :dot-size="14" :width="'70%'" :lazy="true"
            :tooltip="'always'" :interval="1" :adsorb="true" :drag-on-click="false"
            :process-style="{ backgroundColor: '#2a8878' }" @change="handleYearChange" />
        </div>

        <h4 class="filter-title">Links阈值
          <Tip text="低阈值使Links的判定更宽松" />
        </h4>

        <div class="slider-container links-filter">
          <vue-slider v-model="linkThreshold" :min="70" :max="100" :step="1" :dot-size="14" :width="'70%'"
            :tooltip="'none'" :process-style="{ backgroundColor: '#2a8878' }"
            :rail-style="{ backgroundColor: '#e0e0e0' }" :lazy="false" @input="handleThresholdChange" />
          <div class="slider-labels-container">
            <span>低</span>
            <span>高</span>
          </div>
        </div>

        <h4 class="filter-title">Categories 筛选</h4>
        <div class="category-dropdown-container">
          <el-select v-model="selectedCategories" multiple collapse-tags collapse-tags-tooltip :max-collapse-tags="2"
            placeholder="添加筛选..." class="category-select" :popper-append-to-body="false"
            popper-class="category-dropdown-popper" @change="handleCategoryChange">
            <el-option v-for="cat in allCategories" :key="cat" :label="`${cat} (${getCategoryCount(cat)})`"
              :value="cat" />
          </el-select>
        </div>
      </div>
    </div>
  </transition>
</template>
<script setup>
import { ref, watch, computed, nextTick } from 'vue';
import VueSlider from 'vue-slider-component';
import { ElSelect, ElOption } from 'element-plus';
import Tip from '../utils/Tip.vue';
import 'vue-slider-component/theme/antd.css';
import 'element-plus/dist/index.css';

const props = defineProps({
  totalPapers: { type: Number, default: 27 },
  filterCollapsed: { type: Boolean, default: true },
  availableYears: { type: Array, default: () => [] },
  categoryStats: { type: Array, default: () => [] },
  allCategories: { type: Array, default: () => [] },
  categoryFilter: { type: Array, default: () => [] } // ✅ 新增：接收父组件的筛选状态
});

const emit = defineEmits(['paper-count-change', 'year-change', 'threshold-change', 'category-change', 'close']);
const handleClose = () => {
  emit('close');
};
// 弹出窗口位置状态
const popupPosition = ref({ x: 800, y: 100 });
const isDragging = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

// 弹出窗口样式
const popupStyle = computed(() => ({
  left: `${popupPosition.value.x}px`,
  top: `${popupPosition.value.y}px`,
}));

// 默认弹出位置
const defaultPopupPosition = { x: 800, y: 100 };

// 每次 filterCollapsed 从 true → false 时重置位置
watch(
  () => props.filterCollapsed,
  (newVal, oldVal) => {
    if (oldVal === true && newVal === false) {
      popupPosition.value = { ...defaultPopupPosition };
    }
  }
);

// 拖动
const startDrag = (e) => {
  isDragging.value = true;
  dragOffset.value = {
    x: e.clientX - popupPosition.value.x,
    y: e.clientY - popupPosition.value.y
  };

  const onMouseMove = (moveEvent) => {
    if (!isDragging.value) return;

    popupPosition.value = {
      x: moveEvent.clientX - dragOffset.value.x,
      y: moveEvent.clientY - dragOffset.value.y
    };
  };

  const onMouseUp = () => {
    isDragging.value = false;
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
  };

  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
};

// 默认状态逻辑
const sliderValue = ref(50);
const linkThreshold = ref(93);
const selectedCategories = ref([]); 

const handleThresholdChange = (val) => {
  emit('threshold-change', val / 100);
};

const getCategoryCount = (category) => {
  const stat = props.categoryStats.find(item => item.name === category);
  return stat ? stat.value : 0;
};

const handleCategoryChange = () => {
  emit('category-change', [...selectedCategories.value]);
};

//////////// 数量滑块 ////////////////
const handleSliderChange = () => {
  emit('paper-count-change', sliderValue.value);
};

// 同步 totalPapers 变化
watch(
  () => props.totalPapers,
  (newVal) => {
    if (sliderValue.value > newVal) {
      sliderValue.value = newVal;
      emit('paper-count-change', sliderValue.value);
    }
  },
);

watch(
  () => sliderValue.value,
  (newVal) => {
    emit('paper-count-change', newVal);
  },
  { immediate: true }
);

watch(
  () => linkThreshold.value,
  (newVal) => {
    emit('threshold-change', newVal / 100);
  },
  { immediate: true }
);

// 监听父组件categoryFilter
watch(
  () => props.categoryFilter,
  (newFilter) => {
    selectedCategories.value = [...newFilter];
  },
  { immediate: true }
);

//////////// 年份滑块 ////////////////
const minYear = computed(() => props.availableYears[0]);
const maxYear = computed(() => props.availableYears[props.availableYears.length - 1]);
const yearRange = ref([minYear.value, maxYear.value]);

watch(
  () => props.availableYears,
  (years) => {
    if (Array.isArray(years) && years.length > 0) {
      yearRange.value = [years[0], years[years.length - 1]];
    }
  },
  { immediate: true }
);

const handleYearChange = (val) => {
  yearRange.value = val;
  emit('year-change', { start: val[0], end: val[1] });
};

</script>

<style scoped>

.filter-fade-enter-active,
.filter-fade-leave-active {
  transition: all 0.3s ease;
}

.filter-fade-enter-from {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}

.filter-fade-enter-to {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.filter-fade-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.filter-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}

.filter-popup {
  position: fixed;
  width: 400px;
  background: #ffffff;
  border: none;
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  z-index: 2000;
  cursor: move;
  user-select: none;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.filter-popup:hover {
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  transform: translateY(-2px);
}


.filter-popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(90deg, #2a8878, #44b79f);
  padding: 16px 22px;
  color: white;
  border-radius: 14px 14px 0 0;
  cursor: move;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.filter-popup-header .menu-title {
  font-family: "Segoe UI", "微软雅黑", sans-serif;
  font-weight: 600;
  font-size: 1.1rem;
  margin: 0;
  letter-spacing: 0.5px;
  color: white;
  border-bottom: none;
}


.filter-popup-content {
  padding: 20px 25px 25px 25px;
  max-height: 70vh;
  overflow-y: auto;
  cursor: default;
  background: #fafafa;
}

.filter-popup-content::-webkit-scrollbar {
  width: 8px;
}

.filter-popup-content::-webkit-scrollbar-thumb {
  background-color: rgba(42, 136, 120, 0.3);
  border-radius: 4px;
}

.filter-popup-content::-webkit-scrollbar-thumb:hover {
  background-color: rgba(42, 136, 120, 0.6);
}

.filter-title {
  margin: 18px 0 8px;
  color: #333;
  font-weight: 600;
  font-size: 0.95rem;
  border-left: 4px solid #2a8878;
  padding-left: 8px;
  letter-spacing: 0.3px;
  font-family: '幼圆', '宋体';
  text-align: left;
}

.slider-container {
  margin-right: 20px;
  position: relative;
  justify-content: flex-end;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.year-container {
  margin-right: 20px;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.links-filter {
  margin-top: -5px;
  margin-bottom: 25px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.slider-labels-container {
  display: flex;
  justify-content: space-between;
  width: 70%;
  margin-top: -15px;
  font-size: 12px;
  color: #555;
}

.slider-labels-container span {
  font-style: italic;
  font-family: '幼圆', '黑体';
}

.category-dropdown-container {
  margin-bottom: 10px;
  margin-top: 10px;
}

.category-select {
  width: 100%;
  border-radius: 8px;
}


:deep(.el-select .el-select__tags) {
  max-height: 60px;
  overflow-y: auto;
}

:deep(.el-select .el-tag) {
  background-color: #2a8878 !important;
  color: white !important;
  border: none !important;
  border-radius: 6px !important;
  padding: 2px 6px !important;
  font-size: 12px !important;
}

:deep(.el-select .el-tag .el-tag__close) {
  color: white;
  background-color: rgba(255, 255, 255, 0.3);
}

:deep(.el-select .el-tag .el-tag__close:hover) {
  background-color: rgba(255, 255, 255, 0.5);
}

:deep(.el-select .el-input__inner) {
  border-color: #ddd;
}

:deep(.el-select .el-input__inner:focus) {
  border-color: #2a8878;
}

:deep(.el-select .el-input__wrapper) {
  border-radius: 8px;
  border-color: #cfd8dc;
  transition: all 0.2s ease;
}

:deep(.el-select .el-input__wrapper:hover) {
  border-color: #2a8878;
  box-shadow: 0 0 0 2px rgba(42, 136, 120, 0.15);
}

:deep(.el-select-dropdown .el-select-dropdown__item) {
  padding: 8px 12px;
}

:deep(.el-select-dropdown .el-select-dropdown__item.selected) {
  background-color: #2a8878;
  color: white;
}

:deep(.el-select-dropdown .el-select-dropdown__item:hover) {
  background-color: #e6f7f4;
}


:deep(.vue-slider-dot-tooltip-inner) {
  background-color: #ffffff !important;
  border: 1px solid #2a8878 !important;
  border-radius: 6px;
  padding: 3px 6px;
  box-shadow: none !important;
}

:deep(.vue-slider-dot-tooltip-text) {
  color: #2a8878 !important;
  font-weight: 600;
  font-size: 0.85rem;
}

:deep(.vue-slider-dot-tooltip-inner::before) {
  content: "";
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: -6px;
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 6px solid #2a8878;
  z-index: 1001;
}

:deep(.vue-slider-dot-tooltip-inner::after) {
  content: none;
}

:deep(.category-dropdown-popper) {
  position: fixed !important;
  /* ✅ 固定定位 */
  top: auto !important;
  /* ✅ 取消默认的 top */
  bottom: 10px !important;
  /* ✅ 固定在底部 */
  left: 50% !important;
  /* ✅ 水平居中 */
  transform: translateX(-50%) !important;
  /* ✅ 居中修正 */
  max-height: 300px !important;
  /* ✅ 限制最大高度 */
  z-index: 9999 !important;
  /* ✅ 确保在最上层 */
}

:deep(.category-dropdown-popper .el-select-dropdown__list) {
  max-height: 280px !important;
  overflow-y: auto !important;
}

:deep(.category-dropdown-popper .el-select-dropdown__list::-webkit-scrollbar) {
  width: 6px;
}

:deep(.category-dropdown-popper .el-select-dropdown__list::-webkit-scrollbar-thumb) {
  background-color: rgba(42, 136, 120, 0.3);
  border-radius: 3px;
}

:deep(.category-dropdown-popper .el-select-dropdown__list::-webkit-scrollbar-thumb:hover) {
  background-color: rgba(42, 136, 120, 0.6);
}

.close-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  padding: 0px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.2s ease, transform 0.15s ease;
  outline: none;
}

.close-btn img {
  width: 25px;
  height: 25px;
  fill: currentColor;
}

.close-btn:hover {
  background-color: rgba(255, 255, 255, 0.15);
  transform: scale(1.05);
}
</style>