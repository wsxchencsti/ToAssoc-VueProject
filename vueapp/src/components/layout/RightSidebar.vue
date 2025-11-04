<template>
  <aside class="RightSidebar">
    <!-- Filter 窗口 -->
    <FilterPopup :total-papers="totalPapers" :filter-collapsed="filterCollapsed" :available-years="availableYears"
      :category-stats="categoryStats" :all-categories="allCategories" :category-filter="categoryFilter"
      @paper-count-change="$emit('paper-count-change', $event)" @year-change="$emit('year-change', $event)"
      @close="$emit('close')" @threshold-change="$emit('threshold-change', $event)"
      @category-change="$emit('category-change', $event)" />

    <!-- 信息统计栏 -->
    <div class="info-panel">
      <h3 class="menu-title">信息统计栏</h3>
      <div v-if="sparkMode" class="community-overview">
        <h4 class="info-title">社群总览</h4>
        <div ref="communityGraph" class="chart-box"></div>
      </div>
      <h4 class="info-title">相关性分布</h4>
      <div class="info-content">
        <div ref="relevancePieChart" class="chart-box"></div>
      </div>
      <h4 class="info-title">Categories分布
        <Tip text="点击饼图在力导向图上显示分类" />
      </h4>
      <div class="info-content">
        <div ref="categoryPieChart" class="chart-box"></div>
      </div>
      <h4 class="info-title">发表年份</h4>
      <div class="info-content">
        <div ref="yearChart" class="chart-box year-chart"></div>
      </div>

    </div>
  </aside>
</template>

<script setup>
import { ref, watch, computed, onMounted, inject, nextTick } from 'vue';
import * as echarts from 'echarts';
import Tip from '../utils/Tip.vue';
import FilterPopup from '../utils/FilterPopup.vue';

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  totalPapers: { type: Number, default: 27 },
  filterCollapsed: { type: Boolean, default: true },
  sparkMode: { type: Boolean, default: false },
  availableYears: { type: Array, default: () => [] },
  categoryStats: { type: Array, default: () => [] },
  allCategories: { type: Array, default: () => [] },
  categoryFilter: { type: Array, default: () => [] },

});

const emit = defineEmits(['paper-count-change', 'year-change', 'threshold-change', 'category-change', 'close']);
const debugMode = inject('debugMode')
const relevanceData = computed(() => {
  const bins = [0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 1.0];
  const counts = bins.map(() => 0);

  props.nodes.forEach(n => {
    for (let i = 0; i < bins.length - 1; i++) {
      if (n.relevance >= bins[i] && n.relevance < bins[i + 1]) {
        counts[i]++;
        break;
      }
    }
    if (n.relevance === 1.0) counts[counts.length - 1]++;
  });

  return bins.slice(0, -1).map((b, i) => ({
    name: `${b}-${bins[i + 1]}`,
    value: counts[i]
  }))
    .filter(item => item.value > 0);
});

// === ECharts ===
const relevancePieChart = ref(null);
let relevancePieInstance = null;
const categoryPieChart = ref(null);
let categoryPieInstance = null;

onMounted(() => {
  if (relevancePieChart.value) {
    relevancePieInstance = echarts.init(relevancePieChart.value);
    setOption();
  }
});

onMounted(() => {
  if (categoryPieChart.value) {
    categoryPieInstance = echarts.init(categoryPieChart.value);
    setCategoryOption();
  }
});

const getCategoryColor = (category) => {
  const palette = [
    '#e6194b', 
    '#f58231', 
    '#ffe119', 
    '#bf00ff', 
    '#3b0aff', 
    '#ff007f', 
    '#ff8c00', 
    '#ff4500', 
    '#800000', 
    '#9932cc', 
    '#ff1493', 
    '#191970', 
    '#b22222', 
    '#8b008b', 
    '#daa520', 
    '#ff6b6b', 
    '#4ecdc4', 
    '#45b7d1', 
    '#96ceb4', 
    '#feca57', 
    '#ff9ff3', 
    '#54a0ff', 
    '#5f27cd', 
    '#00d2d3', 
    '#ff9f43'  
  ];

  let hash = 0;
  for (let i = 0; i < category.length; i++) {
    hash = category.charCodeAt(i) + ((hash << 5) - hash);
  }
  return palette[Math.abs(hash) % palette.length];
};

const activeCategories = inject('activeCategories');
const toggleCategory = inject('toggleCategory');


const setCategoryOption = () => {
  if (!categoryPieInstance) return;

  const totalCount = props.categoryStats.reduce((sum, item) => sum + item.value, 0);

  categoryPieInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const { name, value, percent } = params;
        const isActive = activeCategories.value.has(name);
        const status = isActive ? '✅ 已显示' : '❌ 未显示';
        return `${name}<br/>数量: ${value} (${percent}%)<br/>${status}<br/>点击切换显示状态`;
      }
    },
    color: props.categoryStats.map(item => getCategoryColor(item.name)),
    legend: {
      show: false,
      formatter: (name) => {
        const item = props.categoryStats.find(i => i.name === name);
        if (!item) return '';
        return item.value / totalCount >= 0.1 ? name : '';
      }
    },
    series: [
      {
        type: 'pie',
        radius: '60%',
        data: props.categoryStats.map(item => ({
          ...item,
          itemStyle: {
            // 激活类别添加边框高亮
            borderWidth: activeCategories.value.has(item.name) ? 3 : 1,
            borderColor: activeCategories.value.has(item.name) ? '#ff0000' : '#fff',
            shadowBlur: activeCategories.value.has(item.name) ? 20 : 10,
            shadowColor: activeCategories.value.has(item.name) ? 'rgba(255,0,0,0.3)' : 'rgba(0,0,0,0.1)'
          },
          label: {
            show: item.value / totalCount >= 0.1,
            position: 'outside',
            formatter: (params) => {
              const isActive = activeCategories.value.has(params.name);
              return `${params.name}: ${params.value}${isActive ? ' ✅' : ''}`;
            },
            fontSize: 10
          }
        })),
        labelLine: { show: true, length: 20, length2: 4 },
        emphasis: {
          itemStyle: {
            shadowBlur: 20,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  });

  categoryPieInstance.off('click'); // 移除旧的事件监听器
  categoryPieInstance.on('click', (params) => {
    if (params.componentType === 'series' && params.data) {
      const categoryName = params.data.name;
      toggleCategory(categoryName);
      // 更新高亮状态
      updateCategoryPieHighlight();
    }
  });
};

const updateCategoryPieHighlight = () => {
  if (!categoryPieInstance) return;

  const option = categoryPieInstance.getOption();
  if (option && option.series && option.series[0]) {
    option.series[0].data = option.series[0].data.map(item => ({
      ...item,
      itemStyle: {
        borderWidth: activeCategories.value.has(item.name) ? 3 : 1,
        borderColor: activeCategories.value.has(item.name) ? '#2a8878' : '#fff',
        shadowBlur: activeCategories.value.has(item.name) ? 20 : 10,
        shadowColor: activeCategories.value.has(item.name) ? 'rgba(255,0,0,0.3)' : 'rgba(0,0,0,0.1)'
      },
      label: {
        ...item.label,
        formatter: (params) => {
          const isActive = activeCategories.value.has(params.name);
          return `${params.name}: ${params.value}${isActive ? ' ✅' : ''}`;
        }
      }
    }));

    categoryPieInstance.setOption(option);
  }
};

watch(activeCategories, () => {
  updateCategoryPieHighlight();
}, { deep: true });


watch(
  () => props.categoryStats,
  () => {
    setCategoryOption();
  },
  { deep: true }
);

onMounted(() => {
  if (categoryPieChart.value) {
    categoryPieInstance = echarts.init(categoryPieChart.value);
    setCategoryOption();
  }
});


import { onUnmounted } from 'vue';
onUnmounted(() => {
  if (categoryPieInstance) {
    categoryPieInstance.off('click');
  }
});




// set option
const setOption = () => {
  if (!relevancePieInstance) return;
  relevancePieInstance.setOption({
    tooltip: { trigger: 'item' },
    color: ['#2a8878', '#ffc857', '#f28f3b', '#e9724c', '#c5283d'],

    series: [
      {
        type: 'pie',
        radius: '60%',  
        data: relevanceData.value,
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}: {c}',
          fontSize: 10
        },
        labelLine: {
          show: true,
          length: 20,
          length2: 4
        },
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.1)',
          borderColor: '#fff',
          borderWidth: 1
        }
      }
    ]
  });
};

watch(relevanceData, () => {
  setOption();
}, { deep: true });

// 监听 categoryStats
watch(
  () => props.categoryStats,
  () => {
    setCategoryOption();
  },
  { deep: true }
);

const yearChart = ref(null);
let yearChartInstance = null;
const yearData = computed(() => {
  const map = new Map();
  props.nodes.forEach(n => {
    if (n.update_date) {
      const year = new Date(n.update_date).getFullYear();
      map.set(year, (map.get(year) || 0) + 1);
    }
  });
  const sorted = Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
  return {
    years: sorted.map(i => i[0]),
    counts: sorted.map(i => i[1])
  };
});

onMounted(() => {
  if (yearChart.value) {
    yearChartInstance = echarts.init(yearChart.value);
    updateYearChart();
  }
});

watch(yearData, updateYearChart, { deep: true });

function updateYearChart() {
  if (!yearChartInstance) return;
  const { years, counts } = yearData.value;
  yearChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 10, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: years,
      axisLabel: { rotate: 45, fontSize: 10 }, // 避免重叠!!!!
      axisLine: { lineStyle: { color: '#ccc' } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#eee' } }
    },
    series: [{
      data: counts,
      type: 'line',
      smooth: true,
      symbolSize: 6,
      lineStyle: { color: '#2a8878', width: 2 },
      itemStyle: { color: '#2a8878' },
      areaStyle: { color: 'rgba(42,136,120,0.15)' }
    }]
  });
}

// === spark mode ===
const communityGraph = ref(null);
let communityGraphInstance = null;

const getCommunityData = computed(() => {

  const map = new Map();
  props.nodes.forEach(n => {
    if (!n.community_id) return;
    map.set(n.community_id, (map.get(n.community_id) || 0) + 1);
  });

  const communities = Array.from(map.entries()).map(([id, count]) => ({
    id,
    count
  }));

  return {
    total: communities.length,
    data: communities
  };
});

onMounted(() => {
  if (communityGraph.value) {
    communityGraphInstance = echarts.init(communityGraph.value);
    drawCommunityGraph();
  }
});


watch(
  () => props.sparkMode,
  async (newVal) => {
    if (newVal) {
      await nextTick()

      setTimeout(() => {
        if (communityGraph.value) {
          if (!communityGraphInstance) {
            communityGraphInstance = echarts.init(communityGraph.value)
          }
          drawCommunityGraph()
          communityGraphInstance.resize() 
        }
      }, 100)
    } else {
      if (communityGraphInstance) {
        communityGraphInstance.dispose()
        communityGraphInstance = null
      }
    }
  },
  { immediate: true }
)
function drawCommunityGraph() {
  if (debugMode.value)
    console.log('drawCommunityGraph called');
  if (!communityGraphInstance) return;
  if (debugMode.value)
    console.log('communityGraphInstance ok1');

  const { total, data } = getCommunityData.value;

  const centerNode = {
    id: 'center',
    symbolSize: 20,
    itemStyle: { color: '#2a8788' },
    label: { show: true, color: '#fff', fontSize: 13, fontWeight: 'bold' }
  };

  const nodes = [
    centerNode,
    ...data.map(c => ({
      id: c.id,
      name: '',
      value: c.count,
      symbolSize: Math.pow(c.count, 1.1) * 2 + 10,
      itemStyle: { color: 'orange' },
      label: { show: false }
    }))
  ];


  const links = data.map(c => ({
    source: 'center',
    target: c.id
  }));


  communityGraphInstance.setOption({
    backgroundColor: 'transparent',
    animation: false,
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: false,
        data: nodes,
        links,
        force: {
          repulsion: 100, 
          edgeLength: 15,
          gravity: 0.05
        },
        lineStyle: {
          color: 'rgba(42,136,120,0.4)',
          width: 1,
          curveness: 0 
        },
        emphasis: { disabled: true },
        silent: true, 
        draggable: false
      }
    ]
  });
}


</script>

<style scoped>
.RightSidebar {
  position: fixed;
  top: 50px;
  right: 0;
  width: 400px;
  height: calc(100vh - 50px);
  background-color: #f9f9f9;
  border-left: 2px solid #ddd;
  box-shadow: -4px 0 8px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  z-index: 1000;
}


.info-panel {
  background-color: #ffffff;
  padding: 15px;
  flex: 1;
  overflow-y: auto;
}

.menu-title {
  font-family: '幼圆', '黑体';
  text-align: left;
  font-size: 1.2rem;
  color: #555;
  margin: 0 0 -5px 0;
}

.info-title {
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

.info-content {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-right: 10px;
}

.chart-box {
  width: 85%;
  height: 200px;
  margin-top: -20px;
  margin-bottom: -35px;
}

.year-chart {
  width: 90%;
  height: 200px;
  margin-top: 1px;
  margin-bottom: -10px;
}

.community-overview {
  margin-top: 15px;
  margin-bottom: -20px;
  text-align: center;
}

.community-overview .chart-box {
  width: 100%;
  height: 220px;
  margin-top: -10px;
}
</style>