<template>
  <Header :loading="loading" :show-search="hasSearched || nodes.length > 0" @search="handleSearch" />
  <SubHeader v-if="hasSearched && nodes.length > 0" :query="query" :loading="loading" :filter-active="!filterCollapsed"
    :debug-mode="isDebugMode" @toggle-filter="handleToggleFilter" @spark-toggle="handleSparkToggle"
    @debug-toggle="handleDebugToggle" />
  <div class="main-area">
    <div v-if="loading" class="loading-overlay">
      <div class="spinner"></div>
      <p>Loading...</p>
    </div>
    <div v-else-if="!hasSearched && nodes.length === 0" class="initial-state">
      <div class="initial-content">
        <Welcome :loading="loading" @search="handleSearch" />
      </div>
    </div>
    <div v-else-if="hasSearched && !loading && nodes.length === 0" class="no-results">
      <div class="no-results-content">
        <h3>搜寻失败</h3>
      </div>
    </div>
    <LeftSidebar ref="leftSidebarRef" v-if="hasSearched && nodes.length > 0" :nodes="filteredNodes" :info="sidebarInfo"
      :hoveredNodeId="hoveredNodeId" :category-stats="categoryStats" @clear-info="clearFixedInfo"
      @select-node="handleNodeClick" @clear-selection="clearSelection" @hover-node="handleSidebarHover"
      @leave-node="handleSidebarLeave" @center-graph="handleCenterGraph" />
    <RightSidebar v-if="hasSearched && nodes.length > 0" :key="sidebarKey" :nodes="filteredNodes"
      :total-papers="sortedNodes.length" :available-years="availableYears" :filter-collapsed="filterCollapsed"
      :all-categories="allCategories" :category-stats="categoryStats" :category-filter="categoryFilter"
      @paper-count-change="handlePaperCountChange" @year-change="handleYearChange"
      @threshold-change="handleThresholdChange" @category-change="categoryFilter = $event" :spark-mode="sparkMode"
      @close="filterCollapsed = true" />
    <Graph v-if="hasSearched && nodes.length > 0" ref="graphRef" :nodes="filteredNodes" :links="filteredLinks"
      :hoveredNodeId="hoveredNodeId" :clearInfoMethod="clearInfoFromSidebar" :selectedNodeId="selectedNodeId"
      :threshold="relevanceThreshold" :spark-mode="sparkMode" @node-hover="handleNodeHover"
      @node-leave="handleNodeLeave" @node-click="handleNodeClick" />
  </div>
</template>

<script setup>
import Header from './components/layout/Header.vue';
import Graph from './components/layout/Graph.vue';
import LeftSidebar from './components/layout/LeftSidebar.vue';
import RightSidebar from './components/layout/RightSidebar.vue';
import SubHeader from './components/layout/SubHeader.vue';
import Welcome from './components/utils/Welcome.vue';

import { ref, computed, onMounted, provide, watch } from 'vue';
const closeSpark = ref(null)
const sidebarKey = ref(0);
defineEmits(['search']);
const isDebugMode = ref(false)
provide('debugMode', isDebugMode)
// 接收子组件的调试切换事件
const handleDebugToggle = (debugState) => {
  console.log('全局调试已', debugState ? '开启' : '关闭')
  isDebugMode.value = debugState
}
/* ------------------ 饼图点击事件 ------------------ */

const getCategoryColor = (category) => {
  const palette = [
    '#e6194b', // 鲜红
    '#f58231', // 橙色
    '#ffe119', // 明黄
    '#bf00ff', // 亮紫
    '#3b0aff', // 纯蓝
    '#ff007f', // 艳粉
    '#ff8c00', // 暗橙
    '#ff4500', // 橙红
    '#800000', // 深红
    '#9932cc', // 深紫
    '#ff1493', // 深粉
    '#191970', // 午夜蓝
    '#b22222', // 火砖红
    '#8b008b', // 深洋红
    '#daa520', // 金色
    '#ff6b6b', // 珊瑚红
    '#4ecdc4', // 蓝绿色（不是绿色系）
    '#45b7d1', // 天蓝色
    '#96ceb4', // 薄荷蓝绿
    '#feca57', // 金黄色
    '#ff9ff3', // 亮粉色
    '#54a0ff', // 亮蓝色
    '#5f27cd', // 深蓝紫
    '#00d2d3', // 青蓝色
    '#ff9f43'  // 橘黄色
  ];

  let hash = 0;
  for (let i = 0; i < category.length; i++) {
    hash = category.charCodeAt(i) + ((hash << 5) - hash);
  }
  return palette[Math.abs(hash) % palette.length];
};
// 在 provide 中添加 getCategoryColor
provide('getCategoryColor', getCategoryColor);
const activeCategories = ref(new Set());
const toggleCategory = (category) => {
  if (isDebugMode.value)
    console.log('点击类别:', category, '当前激活:', Array.from(activeCategories.value));

  if (activeCategories.value.has(category)) {
    activeCategories.value.delete(category); // 再次点击取消
  } else {
    activeCategories.value.add(category); // 第一次点击激活
  }
  // 强制触发响应式更新
  activeCategories.value = new Set(activeCategories.value);
  if (isDebugMode.value)
    console.log('点击后激活:', Array.from(activeCategories.value));
};
provide('activeCategories', activeCategories);
provide('toggleCategory', toggleCategory);

const graphRef = ref(null);
const leftSidebarRef = ref(null);
const clearInfoFromSidebar = computed(() => leftSidebarRef.value?.clearInfo);
const handleCenterGraph = () => {
  // 调用 Graph.vue 中暴露的 smartCenterGraph 方法
  graphRef.value?.smartCenterGraph();
};

/* ------------------ 左侧 sidebar 信息管理 ------------------ */
const sidebarInfo = ref(null);  // 当前显示详细信息
const fixedInfo = ref(null);    // 点击固定节点
const hoveredNodeId = ref(null); // 新增：高亮节点id
const selectedNodeId = ref(null); // ✅ 新增

const handleNodeHover = (node) => {
  hoveredNodeId.value = node?.id;
  sidebarInfo.value = node;
};

const handleNodeLeave = () => {
  hoveredNodeId.value = null;
  sidebarInfo.value = fixedInfo.value || null;
};
const clearSelection = () => {
  fixedInfo.value = null;
  sidebarInfo.value = null;
  hoveredNodeId.value = null;   // 如果你定义了 hoveredNodeId
  selectedNodeId.value = null;  // ✅ 清除 Graph 的高亮
};
const handleNodeClick = (node) => {
  fixedInfo.value = node;
  sidebarInfo.value = node;
  selectedNodeId.value = node.id; // ✅ 记录选中节点
};

const clearFixedInfo = () => {
  fixedInfo.value = null;
  sidebarInfo.value = null;
  hoveredNodeId.value = null; // 清除高亮
};

/* ------------------ Sidebar hover 事件 ------------------ */
const handleSidebarHover = (node) => {
  hoveredNodeId.value = node.id; // 列表 hover → Graph 高亮
};

const handleSidebarLeave = () => {
  hoveredNodeId.value = null; // 列表 leave → 恢复
};

/* ------------------ 数据集 ------------------ */
const nodes = ref([]);
const links = ref([]);
const query = ref('');  // ✅ 修改：默认空查询
const loading = ref(false);
const hasSearched = ref(false); // ✅ 新增：标记是否进行过搜索
const DEBUG_MODE = true;

const resetAllFilters = () => {
  // 重置过滤选项
  categoryFilter.value = [];
  yearFilter.value = { start: null, end: null };
  relevanceThreshold.value = 0.9;
  displayCount.value = 100;

  // 重置图表状态
  selectedNodeId.value = null;
  hoveredNodeId.value = null;
  fixedInfo.value = null;
  sidebarInfo.value = null;

  // 重置饼图激活状态
  activeCategories.value = new Set();

  // 重置图表视图（如果有的话）
  graphRef.value?.resetView?.();
  sidebarKey.value += 1;
};

const fetchNodes = async () => {
  if (!query.value) {
    // ✅ 如果查询为空，清空数据并返回
    nodes.value = [];
    links.value = [];
    hasSearched.value = false;
    return;
  }
  resetAllFilters();

  loading.value = true;
  hasSearched.value = true;
  try {
    let data;
    await new Promise(resolve => setTimeout(resolve, 500));
    if (DEBUG_MODE) {
      let debug_url;
      if (query.value === 'test1') {
        debug_url = '/files/test_new_community1.json';
      } else {
        debug_url = '/files/test_new_community2.json';
      }
      const res = await fetch(debug_url);
      if (!res.ok) throw new Error(`Failed to load mock.json: ${res.status}`);
      data = await res.json();
    } else {
      const res = await fetch(`http://localhost:8000/search?query=${encodeURIComponent(query.value)}`);
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      data = await res.json();
    }
    nodes.value = data.nodes.map(r => ({
      id: r.id,
      arxiv_id: r.arxiv_id,
      title: r.title,
      doi: r.doi,
      comments: r.comments,
      journal_ref: r.journal_ref,
      authors: r.authors,
      categories: r.categories,
      abstract: r.abstract,
      update_date: r.update_date,
      citation_count: r.citation_count,
      relevance: r.relevance,
      community_id: r.community_id
    }));

    links.value = data.links.map(l => ({
      source: l.source,
      target: l.target,
      weight: l.weight
    }));
  } catch (err) {
    console.error(err);
    nodes.value = [];
    links.value = [];
  } finally {
    loading.value = false;
  }
};

const handleSearch = (newQuery) => {
  query.value = newQuery;
  filterCollapsed.value = true;
  fetchNodes();
};

/* ------------------ 排序 & 过滤 ------------------ */
const sortedNodes = computed(() => [...nodes.value].sort((a, b) => b.relevance - a.relevance));
const displayCount = ref(100); // 默认显示 top-100
const handlePaperCountChange = (count) => displayCount.value = count;

const filteredNodes = computed(() => {
  return sortedNodes.value
    .slice(0, displayCount.value)
    .filter(n => {
      // 年份过滤
      const date = n.update_date || '';
      const m = date.match(/^(\d{4})/);
      if (!m) return false;
      const y = parseInt(m[1]);
      if (yearFilter.value.start && yearFilter.value.end) {
        if (y < yearFilter.value.start || y > yearFilter.value.end) return false;
      }
      // category 过滤 - 改为 AND 逻辑
      // category 过滤 - 改为 OR 逻辑 ✅
      if (categoryFilter.value.length > 0 && n.categories) {
        const nodeCats = n.categories.split(' ');
        // 论文只要包含任意一个选中的分类就显示
        const hasAnySelectedCategory = nodeCats.some(nodeCat =>
          categoryFilter.value.includes(nodeCat)
        );
        if (!hasAnySelectedCategory) return false;
      }
      return true;
    });
});

const categoryStats = computed(() => {
  const map = new Map();
  filteredNodes.value.forEach(n => {
    if (n.categories) {
      n.categories.split(' ').forEach(c => {
        map.set(c, (map.get(c) || 0) + 1);
      });
    }
  });
  return Array.from(map.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value); // 按数量降序
});


const filteredLinks = computed(() => {
  const displayedIds = new Set(filteredNodes.value.map(n => n.id));
  if (isDebugMode.value) {
    console.log('displayedIds sample:', Array.from(displayedIds).slice(0, 5));
    console.log('threshold =', relevanceThreshold.value);
    console.log('links total =', links.value.length);
  }

  const filtered = links.value.filter(link => {
    // 两端节点都在展示范围内
    if (!displayedIds.has(link.source) || !displayedIds.has(link.target)) {
      return false;
    }

    // ✅ 修复：使用 link 的 weight 进行阈值过滤
    return link.weight >= relevanceThreshold.value;
  });
  if (isDebugMode.value) {
    console.log('filteredLinks count:', filtered.length);
    console.log('filteredLinks sample:', filtered.slice(0, 3).map(l => ({
      s: l.source,
      t: l.target,
      w: l.weight
    })))
  };

  return filtered;
});

/* ------------------ 页面挂载时不再自动加载 ------------------ */
onMounted(() => {
  // ✅ 移除自动查询，保持界面为空状态
  // 可以在这里添加其他初始化逻辑，比如从 localStorage 恢复状态等
});

const filterCollapsed = ref(true); // 默认收起
// 调试状态
if (isDebugMode.value) {
  console.log('🚀 App.vue - 初始 filterCollapsed:', filterCollapsed.value);
  console.log('🚀 App.vue - 传递给 SubHeader 的 filterActive:', !filterCollapsed.value)
};

watch(filterCollapsed, (newVal) => {
  if (isDebugMode.value) {
    console.log('🔄 App.vue - filterCollapsed 变化:', newVal);
    console.log('🔄 App.vue - 传递给 SubHeader 的 filterActive:', !newVal)
  };
});
// 统一处理 Filter 切换
const handleToggleFilter = () => {
  filterCollapsed.value = !filterCollapsed.value;
  if (isDebugMode.value)
    console.log('🎯 App.vue - handleToggleFilter 被调用');
};

const yearFilter = ref({ start: null, end: null }); // 年份 slider 控制
const countedNodes = computed(() =>
  sortedNodes.value.slice(0, displayCount.value)
);
const availableYears = computed(() => {
  const set = new Set();
  countedNodes.value.forEach(n => {
    const date = n.update_date || '';
    const m = date.match(/^(\d{4})/);
    if (m) set.add(parseInt(m[1]));
  });
  const arr = Array.from(set).sort((a, b) => a - b);
  return arr.length ? arr : [2000, 2025]; // fallback
});
const handleYearChange = (range) => {
  yearFilter.value = range;
};


const relevanceThreshold = ref(0.9)


const handleThresholdChange = (val) => {
  relevanceThreshold.value = val
}

const categoryFilter = ref([]); // 用户选择的类别列表
const allCategories = computed(() => {
  const categoryCounts = new Map();

  // 统计每个分类的出现次数
  nodes.value.forEach(n => {
    if (n.categories) {
      n.categories.split(' ').forEach(c => {
        categoryCounts.set(c, (categoryCounts.get(c) || 0) + 1);
      });
    }
  });

  // 按数量倒序排序
  return Array.from(categoryCounts.entries())
    .sort((a, b) => b[1] - a[1]) // 按数量降序
    .map(([name]) => name); // 只返回分类名称
});
const sparkMode = ref(false) // 控制 spark 模式状态
const handleSparkToggle = (isActive) => {
  sparkMode.value = isActive
  if (isDebugMode.value)
    console.log('sparkmode', isActive)
}

</script>

<style>
/* ============== layout & 基础 ============== */
html,
body {
  margin: 0;
  padding: 0;
  height: 100%;
}

/* Header / SubHeader 保持在页面最上方（它们通常是 fixed 或正常流） */
/* 不强制修改 position，避免改变布局，仅在需要时可提高层级 */
header,
.sub-header {
  /* 如果 header/sub-header 使用 fixed 或 absolute，此 z-index 会生效 */
  z-index: 3000;
}

/* main-area：定位上下文保留，允许内容溢出（防止裁切 SVG） */
.main-area {
  /* position: relative; */
  height: calc(100vh - 100px);
  /* 留出 Header + SubHeader 高度（如需自动测量后面我可以帮你） */
  overflow: visible;
  /* ✅ 不裁剪 Graph/SVG */
  z-index: 1000;
  /* 普通内容层 */
}

/* ============== 遮罩 ============== */
/* 注意：overlay 是 main-area 的子元素，使用 absolute inset 覆盖整个 main-area */
.loading-overlay {
  position: fixed;
  inset: 0;
  /* top:0; right:0; bottom:0; left:0; */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.85);
  z-index: 1200;
  /* 高于 graph/sidebars（默认 1000），但低于 header（3000） */
  pointer-events: auto;
  /* 拦截点击（可根据需要改为 none） */
}

/* ============== 初始状态样式 ============== */
.initial-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  z-index: 1100;
}

.initial-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 0;
  width: auto;
  /* ✅ 让内容自然宽度居中 */
}

.initial-content h3 {
  color: #2a8878;
  font-size: 1.8rem;
  margin-bottom: 16px;
  font-weight: 600;
}

.initial-content p {
  color: #666;
  font-size: 1.1rem;
  margin-bottom: 30px;
}

.tips {
  text-align: left;
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.tip-item ul {
  margin: 10px 0 0 0;
  padding-left: 20px;
  color: #555;
}

.tip-item li {
  margin-bottom: 8px;
  line-height: 1.4;
}

/* ============== 空结果提示 ============== */
.no-results {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}

.no-results-content {
  text-align: center;
}

.no-results-content h3 {
  color: #666;
  font-size: 1.5rem;
  margin-bottom: 12px;
}

.no-results-content p {
  color: #888;
  font-size: 1rem;
}

/* 如果你想 overlay 不拦截点击但仍可见（通常不建议）：
.loading-overlay { pointer-events: none; }
.spinner { pointer-events: auto; } 
*/

/* ============== spinner / 文本 ============== */
.spinner {
  width: 48px;
  height: 48px;
  border: 5px solid rgba(0, 0, 0, 0.12);
  border-top-color: #2a8878;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  /* 不需要单独 z-index（在 overlay 内） */
}

.loading-text {
  margin-top: 12px;
  font-size: 1.05rem;
  font-weight: 600;
  color: #2a8878;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial;
}

/* ============== Graph / Sidebars 层级 ============== */
/* Graph 根容器：确保有高度并位于内容层 */
.graph-container {
  position: relative;
  z-index: 1000;
  /* 低于 overlay */
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* 如果你的 Left/Right Sidebar 是 fixed（根据你的实现），控制他们的 z-index：*/
.LeftSidebar,
.RightSidebar {
  z-index: 1000;
  /* 被 overlay 覆盖（如果你想 overlay 覆盖 sidebars） */
  /* 如果你希望 sidebars 在 overlay 之上（即不被遮罩），把它们设为 z-index:1300 */
}

/* ============== 动画 ============== */
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>