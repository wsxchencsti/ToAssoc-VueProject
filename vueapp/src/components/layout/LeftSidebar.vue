<!-- leftsidebar.vue -->
<template>
  <div class="SidebarWrapper" @mouseenter="hoverSidebar" @mouseleave="leaveSidebar">
    <aside :class="['LeftSidebar', { collapsed, 'detail-mode': info }]">
      <transition name="slide-fade" mode="out-in">
        <div v-if="!collapsed" class="sidebar-content" :key="info ? 'detail' : 'list_view'">
          <template v-if="info">
            <!-- 详细信息时返回按钮 -->
            <div class="back-section" @click="clearInfo">
              <img src="/icons/leftsidebar/format_list_bulleted.svg" alt="to-list">
              <p>Back to list</p>
            </div>
            <h2 class="paper-title">{{ info.title }}</h2>
            <p class="paper-author">
              {{ info.authors }}
            </p>
            <p class="paper-year">
              {{ info.update_date }}
            </p>
            <div class="paper-categories">
              <span class="categories-label">Categories:</span>
              <div class="category-tags">
                <span v-for="category in info.categories.split(' ')" :key="category" class="category-tag" :style="{
                  backgroundColor: getCategoryColor(category),
                  outline: activeCategories.has(category) ? '2px solid #296a5f' : 'none'
                }" @click="toggleCategory(category)">
                  {{ category }}
                  <span class="category-count">
                    ({{ getCategoryCount(category) }})
                  </span>
                </span>
              </div>
            </div>

            <p class="paper-comments" v-if="info.comments && info.comments.trim() !== ''">
              Comments: {{ info.comments }}
            </p>
            <p class="paper-relevance">
              Relevance to the topic: {{ info.relevance.toFixed(5) }}
            </p>

            <p class="search-in">
              search in:
              <a :href="'https://arxiv.org/search/?query=' + encodeURIComponent(info.title) + '&searchtype=title&abstracts=show&order=-announced_date_first&size=50'"
                target="_blank" title="arxiv">
                <img src="/icons/leftsidebar/arxiv_small.svg" alt="arxiv" />
              </a>
              <a :href="'https://scholar.google.com/scholar?q=' + encodeURIComponent(info.title)" target="_blank"
                title="Google Scholar">
                <img src="/icons/leftsidebar/google_scholar.svg" alt="google_scholar" />
              </a>
              <a :href="'https://www.semanticscholar.org/search?q=' + encodeURIComponent(info.title) + '&sort=relevance'"
                target="_blank" title="Semantic Scholar">
                <img src="/icons/leftsidebar/Semantic_Scholar.svg" alt="Semantic_Scholar" />
              </a>
            </p>
            <div class="paper-abstract">
              <div class="abstract-word">
                <hr color="#296a5f">
                Abstract
              </div>
              <p>{{ info.abstract }}</p>
            </div>
          </template>
          <template v-else>
            <!-- 列表 -->
            <ul class="node-list">
              <li v-for="(node, index) in nodes" :key="node.id" @click="$emit('select-node', node)"
                @mouseenter="$emit('hover-node', node)" @mouseleave="$emit('leave-node')" class="node-item">
                <div class="node-index">{{ index + 1 }}</div>
                <div class="node-content">
                  <h4 class="node-title">{{ node.title }}</h4>
                  <p class="node-author">{{ node.authors }}</p>
                  <p class="node-year">{{ node.update_date }}</p>
                </div>
              </li>
            </ul>

          </template>
        </div>
      </transition>
    </aside>

    <!-- 外侧按钮+黑色填充 -->
    <div :class="['toggle-btn-wrapper', collapsed ? 'collapsed' : 'expanded', { visible: showBtn }]"
      @click="toggleSidebar">
      <img class="toggle-btn" :src="collapsed ? '/icons/leftsidebar/arrow_circle_right.svg' : '/icons/leftsidebar/arrow_circle_left.svg'"
        alt="toggle sidebar" />
    </div>
    <div :class="['sidebar-tools', collapsed ? 'collapsed' : 'expanded', { visible: showBtn }]">

      <div class="tool-row">
        <button class="tool-btn" title="居中视图" @click="centerGraph">
          <img src="/icons/leftsidebar/center_location.svg" alt="Center" />
        </button>
      </div>

      <div class="year-scale-section">
        <YearScale />
      </div>
    </div>

  </div>
</template>

<script setup>
import { defineComponent, ref, inject } from "vue";
import YearScale from "../utils/YearScale.vue";
const props = defineProps({
  nodes: { type: Array, default: () => [] },
  info: { type: Object, default: null },
  categoryStats: { type: Array, default: () => [] }
});
const emit = defineEmits(["clear-info", "select-node", "clear-selection", "center-graph"]);
// 注入父组件的方法和状态
const getCategoryColor = inject('getCategoryColor');
const activeCategories = inject('activeCategories');
const toggleCategory = inject('toggleCategory');
const categoryStats = inject('categoryStats'); // 需要在父组件中提供


const collapsed = ref(false);
const showBtn = ref(false);
let hideTimer = null;

const toggleSidebar = () => {
  collapsed.value = !collapsed.value;
};

const hoverSidebar = () => {
  showBtn.value = true;
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
};

const leaveSidebar = () => {
  hideTimer = setTimeout(() => {
    showBtn.value = false; // 按钮延迟消失
  }, 300);
};

const clearInfo = () => {
  emit("clear-info");
  emit("clear-selection");
};
defineExpose({
  clearInfo
});
const centerGraph = () => {
  emit("center-graph");
};
const getCategoryCount = (category) => {
  const found = props.categoryStats.find(item => item.name === category);
  return found ? found.value : 0;
};
</script>

<style scoped>
.SidebarWrapper {
  position: fixed;
  top: 50px;
  left: 0;
  height: calc(100vh - 50px);
  z-index: 1000;
}

/* 左栏 */
.LeftSidebar {
  margin-top: 30px;
  text-align: left;
  padding: 0px 1px;
  width: 450px;
  height: 100%;
  background-color: #f9f9f9;
  border-right: 2px solid #ddd;
  box-shadow: 4px 0 8px rgba(0, 0, 0, 0.1);
  box-sizing: border-box;
  transition: width 0.3s ease, padding 0.3s ease;
  overflow-y: auto;
  /* 整个 sidebar 滚动 */
  white-space: normal;
  word-break: break-word;
  overflow-wrap: break-word;
  line-height: 1.5;
  font-size: 16px;
  /* border-bottom: 10px; */
}

.LeftSidebar.detail-mode {
  padding: 0px 25px;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: opacity 0.1s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
}

.slide-fade-enter-to,
.slide-fade-leave-from {
  opacity: 1;
}

.LeftSidebar.collapsed {
  width: 1px;
  padding: 5px;
}

/* paper 内容样式 */
.paper-title {
  font-family: 'Times New Roman', Times, serif;
  font-size: 20px;
  font-weight: bold;
  margin-top: 10px;
  margin-bottom: 10px;
  color: #333;
}

.paper-author,
.paper-categories,
.paper-doi,
.paper-comments,
.paper-year,
.search-in,
.paper-relevance {
  font-size: 16px;
  margin: 4px 0 10px 0;
  color: #817b7b;
  word-break: break-word;
  overflow-wrap: break-word;
}


.abstract-word {
  font-size: 16px;
  color: #817b7b;
}

.paper-author {
  word-break: break-all;
}

.search-in a {
  display: inline-flex;
  /* 让内容水平/垂直居中 */
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  width: 35px;
  /* 固定宽度 */
  height: 35px;
  /* 固定高度 */
  border-radius: 4px;
  /* 圆角 */
  transition: background 0.2s;
}

.search-in a:hover {
  background-color: #d4d4d4;
  /* 灰底 hover 效果 */
}

.search-in img {
  width: 25px;
  /* 图标大小 */
  height: 25px;
  object-fit: contain;
}

.paper-abstract {
  font-size: 15px;
  color: #222;
  line-height: 1.6;
  text-align: left;
  /* 左对齐 */
  /* hyphens: auto;               */
  word-break: normal;
  overflow-wrap: anywhere;
  padding-bottom: 100px;
}

.paper-abstract p {
  margin-top: 4px;
}

/* 占位提示 */
.list_view {
  text-align: center;
  color: #888;
}

/* 按钮外层黑色圆形背景 + 发光 */
.toggle-btn-wrapper {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: rgba(33, 33, 33, 0.8);
  box-shadow: 0 0 12px 2px rgba(0, 0, 0, 0.9);
  display: flex;
  justify-content: center;
  align-items: center;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.5s ease, left 0.3s ease;
  z-index: 1090;
  cursor: pointer;
}

.toggle-btn-wrapper.expanded {
  left: 455px;
}

.toggle-btn-wrapper.collapsed {
  left: 15px;
}

.toggle-btn-wrapper.visible {
  opacity: 1;
  pointer-events: auto;
}

.toggle-btn {
  width: 48px;
  height: 48px;
  z-index: 1100;
}

/* 内容淡入淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.back-section {
  margin-top: 30px;
  padding: 5px 10px;
  background: #eaeaea;
  color: #333;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.2s;

  display: inline-flex;
  /* 使内容水平排列 */
  align-items: center;
  /* 垂直居中 */
  justify-content: center;
  /* 水平居中 */
  gap: 6px;
  /* 文字和图标间距 */
}

.back-section p {
  color: #296a5f;
  font-size: 14px;
  margin: 0;
  /* 去掉默认 margin */
}

.back-section img {
  width: 20px;
  /* 调整图标大小 */
  height: 20px;
  object-fit: contain;
}

.back-section:hover {
  background: #e0e0e0;
}

.slide-vertical-enter-active,
.slide-vertical-leave-active {
  transition: all 0.1s ease;
}

.slide-vertical-enter-from {
  opacity: 0;
  transform: translateY(-50px);
  /* 从下方进入 */
}

.slide-vertical-enter-to {
  opacity: 1;
  transform: translateY(0);
}

.slide-vertical-leave-from {
  opacity: 1;
  transform: translateY(0);
}

.slide-vertical-leave-to {
  opacity: 0;
  transform: translateY(-100px);
  /* 向上消失 */
}

.node-list {
  margin-left: 0;
  margin-right: 0;
  list-style: none;
  padding: 0;
  margin-top: 20px;
}

.node-item {
  display: flex;
  align-items: center;
  /* 关键：垂直居中序号与内容块 */
  gap: 12px;
  /* 序号与内容间距 */
  width: 100%;
  box-sizing: border-box;
  padding: 10px 8px;
  cursor: pointer;
  border-bottom: 1px solid #ddd;
  transition: background 0.2s;
}

/* 序号样式：白字 + 绿色背景，固定大小，垂直居中 */
.node-index {
  flex: 0 0 25px;
  /* 固定宽度 */
  height: 25px;
  /* 固定高度 */
  display: flex;
  align-items: center;
  justify-content: center;
  background: #296a5f;
  /* 绿色背景（可按你的配色替换） */
  color: #ffffff;
  font-weight: 700;
  border-radius: 12px;
  font-size: 10px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.05) inset;
}

/* 内容列占满剩余空间 */
.node-content {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  /* 让内部的截断生效 */
}

/* 保留并加强你的截断样式（支持 -webkit 和 标准 line-clamp 尝试） */
.node-title {
  font-size: 14px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  /* 非标准，但主流浏览器支持 */
  line-clamp: 2;
  /* 草案标准，未来可能支持 */
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
  max-height: calc(1.4em * 2);
  margin: 0;
}

/* 作者同样修复截断 */
.node-author,
.node-year {
  font-size: 12px;
  color: #666;
  margin: 0;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  /* 草案标准，未来可能支持 */
  /* 作者只显示一行 */
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
  max-height: 1.4em;
}

/* 保留 hover 高亮效果 */
.node-item:hover {
  background-color: #ececec;
  border-radius: 16px;
}

/* ========= 辅助工具按钮区域 ========= */
.sidebar-tools {
  position: absolute;
  top: calc(96%);
  left: 0;
  transform: translateY(-55%);
  display: flex;
  flex-direction: column;
  gap: 8px;

  /* 动画和按钮保持一致 */
  opacity: 1;
  pointer-events: auto;
  transition: opacity 0.5s ease, left 0.3s ease;

  z-index: 1085;
  align-items: flex-start;
  padding-left: 1px;
}

.sidebar-tools.expanded {
  left: 455px;
  /* 保持位置和按钮一致 */
  opacity: 1;
  pointer-events: auto;
}

.sidebar-tools.collapsed {
  left: 15px;
  /* 保持位置和按钮一致 */

}


.tool-row {
  display: flex;
  justify-content: flex-start;
}

.year-scale-section {
  width: 100%;
  display: flex;
  justify-content: flex-start;
  margin-left: -5px;
  margin-top: -10px;
}

/* 单个工具按钮 */
.tool-btn {
  width: 28px;
  /* 🔹比原来40px小一点 */
  height: 28px;
  border-radius: 50%;
  background-color: #ffffff;
  border: 1px solid #2a8878;
  /* 🔹绿色描边 */
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 4px;
  /* 🔹让内部 img 留一点空间 */
  /* ✅ 禁止默认点击黑边 */
  outline: none;
  box-shadow: none;
}

.tool-btn:hover {
  background-color: #f3fdfb;
  box-shadow: 0 0 8px rgba(41, 106, 95, 0.5);
  transform: scale(1.05);
}

/* 按钮内部的图片 */
.tool-btn img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
}

.categories-label {
  font-size: 16px;
  color: #817b7b;
  margin-right: 8px;
  display: inline;
  vertical-align: top;
}

.category-tags {
  display: inline;
  margin-top: 0;
}

.category-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  color: white;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  margin: 2px 6px 2px 0;
  /* 上右下左：上下2px，右边6px，左边0 */
  vertical-align: middle;
}

.paper-categories {
  margin: 10px 0;
  line-height: 1.4;
  display: block;
}

.category-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.3);
}

.category-count {
  font-size: 10px;
  margin-left: 4px;
  opacity: 0.9;
}

.paper-categories {
  margin: 10px 0;
  line-height: 1.4;
}
</style>
