<!-- graph.vue -->
<template>
  <div class="graph-container" ref="graphContainer"></div>
</template>
<!-- 111000000 -->
<script setup>
import * as d3 from "d3";
import { onMounted, ref, watch, nextTick, onBeforeUnmount, defineExpose, inject } from "vue";

const props = defineProps({
  nodes: { type: Array, required: true },
  links: { type: Array, required: true },
  hoveredNodeId: { type: String, default: null },
  selectedNodeId: { type: String, default: null },
  threshold: { type: Number, default: 0.9 },
  clearInfoMethod: { type: Function, default: null },
  sparkMode: Boolean
});

// Set<string>
const activeCategories = inject('activeCategories');
const getCategoryColor = inject('getCategoryColor');
const debugMode = inject('debugMode')

const updateNodeRings = () => {
  if (!g || !simulation || !svg) return;

  g.selectAll("circle").each(function (d) {
    const nodeElement = d3.select(this);
    const nodeCategories = d.categories ? d.categories.split(' ') : [];
    const activeNodeCategories = nodeCategories.filter(cat => activeCategories.value.has(cat));

    const isHovered = d.id === props.hoveredNodeId;
    const isSelected = d.id === props.selectedNodeId;

    /////// 结点选中 ///////
    let strokeWidth = 10;
    if (isSelected) strokeWidth += 3;
    if (isHovered) strokeWidth += 2;

    const t = nodeElement.transition().duration(150);

    if (activeNodeCategories.length > 0) {
      if (activeNodeCategories.length === 1) {
        const borderColor = getCategoryColor(activeNodeCategories[0]);
        t.attr('stroke', borderColor)
          .attr('stroke-width', strokeWidth)
          .attr('stroke-opacity', 0.8);
      } else {
        const gradientId = `gradient-${d.id}`;
        const defs = svg.select("defs");

        defs.select(`#${gradientId}`).remove();

        const gradient = defs.append("linearGradient")
          .attr("id", gradientId)
          .attr("x1", "0%")
          .attr("y1", "100%")
          .attr("x2", "100%")
          .attr("y2", "0%");

        activeNodeCategories.forEach((cat, index) => {
          const color = getCategoryColor(cat);
          const startOffset = (index / activeNodeCategories.length) * 100;
          const endOffset = ((index + 1) / activeNodeCategories.length) * 100;

          gradient.append("stop")
            .attr("offset", `${startOffset}%`)
            .attr("stop-color", color);
          gradient.append("stop")
            .attr("offset", `${endOffset}%`)
            .attr("stop-color", color);
        });

        t.attr('stroke', `url(#${gradientId})`)
          .attr('stroke-width', strokeWidth)
          .attr('stroke-opacity', 0.8);
      }
    } else {
      // 没有激活类别的情况，保持原先
      if (isHovered || isSelected) {
        t.attr('stroke', isSelected ? 'steelblue' : 'steelblue')
          .attr('stroke-width', isHovered ? 12 : (isSelected ? 12 : 0))
          .attr('stroke-opacity', 0.8);
      } else {
        t.attr('stroke', 'none')
          .attr('stroke-width', 0);
      }
    }
  });
};


watch(() => props.hoveredNodeId, () => {
  if (!g) return;
  updateNodeRings(); // 仅重新绘制边框样式
});

// 监听选中动作
watch(() => props.selectedNodeId, (newId) => {
  if (!g || !svg || !zoom) return;
  updateNodeRings();
  updateHighlight();

  if (!newId) {
    smartCenterGraph();
    return;
  }

  // 居中逻辑
  const node = simulation?.nodes()?.find(n => n.id === newId);
  if (!node) return;

  const container = graphContainer.value;
  const width = container.clientWidth;
  const height = container.clientHeight;
  const targetScale = 1;

  const translate = [
    width / 2 - node.x * targetScale,
    height / 2 - node.y * targetScale
  ];

  svg.transition()
    .duration(600)
    .ease(d3.easeCubicOut)
    .call(zoom.transform, d3.zoomIdentity.translate(translate[0], translate[1]).scale(targetScale));
});



const emit = defineEmits(["node-hover", "node-leave", "node-click"]);

const graphContainer = ref(null);
let simulation = null;
let svg = null;
let g = null;
let zoom = null;
let nodeRadiusScale = null;
let currentTransform = d3.zoomIdentity;
let resizeHandler = null;

// author处理（截断
const truncateAuthor = (author) => {
  if (!author) return "";
  const commaIndex = author.indexOf(",");
  if (commaIndex === -1) return author;
  return author.substring(0, commaIndex);
};

/////////// 初始化节点位置 /////////////
const initializeNodePositions = (nodes, container, oldNodes = []) => {
  if (!container) return nodes;
  const width = container.clientWidth;
  const height = container.clientHeight;
  const oldNodeMap = new Map(oldNodes.map(n => [n.id, n]));
  return nodes.map(node => {
    const oldNode = oldNodeMap.get(node.id);
    if (oldNode) {
      return { ...oldNode, ...node };
    } else {
      return {
        ...node,
        x: Math.random() * (width - 200) + 100,
        y: Math.random() * (height - 200) + 100
      };
    }
  });
};

////// 初始化图表 ////////
const initGraph = async () => {
  await nextTick();
  const container = graphContainer.value;
  if (!container) return;

  // 清空旧内容
  d3.select(container).selectAll("*").remove();

  svg = d3.select(container)
    .append("svg")
    .attr("width", "100%")
    .attr("height", "100%")
    .style("cursor", "grab");

  // 渐变背景
  const defs = svg.append("defs");
  const bgGradient = defs.append("linearGradient")
    .attr("id", "bgGradient")
    .attr("x1", "0%")
    .attr("y1", "0%")
    .attr("x2", "100%")
    .attr("y2", "100%");

  bgGradient.append("stop")
    .attr("offset", "0%")
    .attr("stop-color", "#d0d0d0");
  bgGradient.append("stop")
    .attr("offset", "50%")
    .attr("stop-color", "#f8f8f8");
  bgGradient.append("stop")
    .attr("offset", "100%")
    .attr("stop-color", "#ffffff");

  svg.append("rect")
    .attr("width", "100%")
    .attr("height", "100%")
    .attr("fill", "url(#bgGradient)");

  // 阴影和滤镜
  const shadow = defs.append("filter")
    .attr("id", "nodeShadow")
    .attr("x", "-50%")
    .attr("y", "-50%")
    .attr("width", "200%")
    .attr("height", "200%");

  shadow.append("feDropShadow")
    .attr("dx", 1)
    .attr("dy", 4)
    .attr("stdDeviation", 6)
    .attr("flood-color", "rgba(70,130,180,0.9)")
    .attr("flood-opacity", 0.6);

  const edgeGlow = defs.append("filter")
    .attr("id", "edgeGlow")
    .attr("x", "-50%")
    .attr("y", "-50%")
    .attr("width", "200%")
    .attr("height", "200%");

  edgeGlow.append("feGaussianBlur")
    .attr("in", "SourceGraphic")
    .attr("stdDeviation", 4)
    .attr("result", "blur");

  edgeGlow.append("feMerge")
    .selectAll("feMergeNode")
    .data(["blur", "SourceGraphic"])
    .enter()
    .append("feMergeNode")
    .attr("in", d => d);
  //////// 主图层 ///////////
  g = svg.append("g");

  // 缩放控制
  zoom = d3.zoom()
    .scaleExtent([0.2, 4])
    .on("zoom", event => {
      g.attr("transform", event.transform);
      currentTransform = event.transform;
    });

  svg.call(zoom);
  // 点击同样可以取消选中样式 
  svg.on("click", (event) => {
    if (event.target.tagName === "rect") {
      props.clearInfoMethod?.();
    }
  });
};


// drag 行为
function createDragBehavior(sim) {
  function dragstarted(event, d) {
    if (event.sourceEvent) event.sourceEvent.stopPropagation();
    sim.alphaTarget(0.2).restart(); // 拖动时临时激活
    d.fx = d.x;
    d.fy = d.y;
    // d3.select(this).attr("fill", "white");
  }
  function dragged(event, d) {
    d.fx = event.x;
    d.fy = event.y;
  }
  function dragended(event, d) {
    d.fx = null;
    d.fy = null;
    sim.alphaTarget(0); // 松手后立即静止
    // d3.select(this).attr("fill", "white");
  }
  return d3.drag()
    .subject((event, d) => d)
    .on("start", dragstarted)
    .on("drag", dragged)
    .on("end", dragended);
}

// 力布局
const applyForces = (nodesWithPosition, linksData, container, radiusScale) => {
  const baseStrength = -90;
  const minStrength = -50;
  const centerX = container.clientWidth / 2;
  const centerY = container.clientHeight / 2;

  simulation = d3.forceSimulation(nodesWithPosition)

    .force("link", d3.forceLink(linksData)
      .id(d => d.id)
      .distance(d => {
        const threshold = props.threshold;

        const baseDistance = 400 - (threshold * 150);

        const weightFactor = 0.3 + (1 - d.weight) * 4;

        const calculatedDistance = baseDistance * weightFactor;

        const minDistance = 100;
        const maxDistance = 5000;

        return Math.max(minDistance, Math.min(calculatedDistance, maxDistance));
      })
      .strength(d => 0.1 + 0.01 * d.weight)
    )
    .force("charge", d3.forceManyBody().strength(d => {
      const connectedLinks = linksData.filter(
        l => l.source.id === d.id || l.target.id === d.id
      );
      const maxWeight = connectedLinks.length > 0
        ? Math.max(...connectedLinks.map(l => l.weight))
        : 0;

      const adjusted = baseStrength * (1 - 0.5 * maxWeight);
      return Math.max(minStrength, adjusted);
    }))
    .force("center", d3.forceCenter(centerX, centerY))
    // 防止离群过远
    .force("gravity", d3.forceRadial(
      Math.min(centerX, centerY) * 0.3,
      centerX,
      centerY
    ).strength(0.005))
    .force("collision", d3.forceCollide().radius(d => radiusScale(d.relevance) + 10));

};

// 更新图表
const updateGraphData = () => {
  if (!g || !graphContainer.value) return;
  const container = graphContainer.value;

  // 初始化节点位置
  const oldNodes = simulation ? simulation.nodes() : [];
  const nodesWithPosition = initializeNodePositions(props.nodes, container, oldNodes);

  // 计算相对比例尺
  const relevanceExtent = d3.extent(nodesWithPosition, d => d.relevance || 0.8);
  const minRelevance = relevanceExtent[0] ?? 0.7;
  const maxRelevance = relevanceExtent[1] ?? 0.9;
  const rMin = 40;
  const rMax = 100;

  nodeRadiusScale = d3.scaleLinear()
    .domain([minRelevance, maxRelevance])
    .range([rMin, rMax])
    .clamp(true);

  const getFilteredLinks = () => {
    const threshold = props.threshold;
    const maxLinksPerNode = 6;
    let filteredLinks = props.links.filter(l => l.weight >= threshold);

    const linkGroups = new Map();
    filteredLinks.forEach(l => {
      const key = String(l.source);
      if (!linkGroups.has(key)) linkGroups.set(key, []);
      linkGroups.get(key).push(l);
    });

    let limitedLinks = [];
    linkGroups.forEach(group => {
      group.sort((a, b) => b.weight - a.weight);
      limitedLinks.push(...group.slice(0, maxLinksPerNode));
    });

    const nodeMap = new Map(nodesWithPosition.map(n => [String(n.id), n]));
    return limitedLinks.map(l => ({
      source: nodeMap.get(String(l.source)),
      target: nodeMap.get(String(l.target)),
      weight: l.weight
    })).filter(l => l.source && l.target);
  };

  const linksData = getFilteredLinks();

  // 更新力系统
  if (!simulation) {
    applyForces(nodesWithPosition, linksData, container, nodeRadiusScale);
  } else {
    simulation.stop();
    simulation.nodes(nodesWithPosition);

    const linkForce = simulation.force("link");
    if (linkForce) linkForce.links(linksData);

    simulation.force("collision", d3.forceCollide().radius(d => nodeRadiusScale(d.relevance) + 10));
  }

  // --- 绘制 ---

  // 提取年份
  nodesWithPosition.forEach(d => {
    if (d.update_date) {
      const match = d.update_date.match(/^(\d{4})/);
      d.year = match ? +match[1] : 2020;
    } else {
      d.year = 2020;
    }
  });

  // 颜色比例尺
  const yearExtent = d3.extent(nodesWithPosition, d => d.year || 2020);
  const colorScale = d3.scaleLinear()
    .domain([yearExtent[0] || 2000, yearExtent[1] || 2025])
    .range(["rgb(90, 166, 155)", "rgb(3, 44, 38)"]);

  const nodeSel = g.selectAll("circle").data(simulation.nodes(), d => d.id);
  nodeSel.exit().remove();

  const nodeEnter = nodeSel.enter()
    .append("circle")

    .attr("filter", "url(#nodeShadow)")
    .attr("r", d => nodeRadiusScale(d.relevance))
    .attr("fill", d => colorScale(d.year || 2020))
    .attr("fill-opacity", 0.7)
    .attr("data-fill", d => colorScale(d.year || 2020))
    .on("mouseover", (event, d) => {
      // d3.select(event.currentTarget).attr("fill", "white");
      emit("node-hover", d);
    })
    .on("mouseout", (event, d) => {

      d3.select(event.currentTarget).attr("fill", d => colorScale(d.year || 2020))
        .attr("fill-opacity", 0.7)
      emit("node-leave", d);
    })
    .on("click", (event, d) => emit("node-click", d));

  nodeEnter.merge(nodeSel).call(createDragBehavior(simulation));

  const linkSel = g.selectAll("line").data(linksData, d => `${d.source.id}-${d.target.id}`);
  linkSel.exit().remove();

  const linkColorScale = d3.scaleLinear()
    .domain([0.5, 1])
    .range(["white", "black"])
    .clamp(true);

  linkSel.enter()
    .append("line")
    .merge(linkSel)
    .attr("stroke", function (d) {
      const color = linkColorScale(Math.pow(d.weight, 1.8));
      d3.select(this).attr("data-stroke", color);
      return color;
    })
    .attr("stroke-width", 3);

  const label = g.selectAll("g.label").data(simulation.nodes(), d => d.id);
  label.exit().remove();
  const labelEnter = label.enter()
    .append("g")
    .attr("class", "label")
    .style("pointer-events", "none")
    .each(function (d) {
      // === 解析年份 ===
      const match = d.update_date?.match(/^(\d{4})/);
      const year = match ? match[1] : "N/A";

      // === 作者截断 ===
      let author = truncateAuthor(d.authors) || "Unknown";
      const maxLen = 6;
      if (author.length > maxLen) {
        author = author.substring(0, maxLen) + "…";
      }

      // === 字体大小 & 布局 ===
      const fontSize = Math.max(12, Math.min(18, nodeRadiusScale(d.relevance) / 3.5));
      const lineHeight = fontSize * 1.3;
      const gLabel = d3.select(this);

      // === 年份在上 ===
      gLabel.append("text")
        .attr("text-anchor", "middle")
        .attr("fill", "black")
        .attr("font-weight", "bold")
        .attr("font-size", fontSize)
        .attr("y", -lineHeight / 0.8)
        .attr("stroke", "white")
        .attr("stroke-width", 1)
        .attr("paint-order", "stroke")
        .text(year);

      // === 作者在下 ===
      gLabel.append("text")
        .attr("text-anchor", "middle")
        .attr("fill", "black")
        .attr("font-weight", "bold")
        .attr("font-size", fontSize)
        .attr("y", lineHeight / 8.3)
        .attr("stroke", "white")
        .attr("stroke-width", 1)
        .attr("paint-order", "stroke")
        .text(author);
    });


  labelEnter.merge(label);

  simulation.on("tick", () => {
    g.selectAll("line")
      .attr("x1", d => d.source.x)
      .attr("y1", d => d.source.y)
      .attr("x2", d => d.target.x)
      .attr("y2", d => d.target.y);


    g.selectAll("circle")
      .attr("cx", d => d.x)
      .attr("cy", d => d.y);

    g.selectAll("g.label")
      .attr("transform", d => `translate(${d.x}, ${d.y})`);
  });
  updateNodeRings();
  simulation.alpha(3).restart();
  // links 在最下层
  g.selectAll("line").lower();
  // 节点在 links 之上
  g.selectAll("circle").raise();
  // 标签在最上层
  g.selectAll("g.label").raise();
};

// 高亮选中节点及其邻居
function updateHighlight() {
  if (!g) return;

  const selectedId = props.selectedNodeId;
  const linkSel = g.selectAll("line");
  const nodeSel = g.selectAll("circle");

  // --- 重置所有边 ---
  linkSel
    .attr("stroke", function () { return d3.select(this).attr("data-stroke"); })
    .attr("stroke-width", 3)
    .attr("opacity", 0.8);

  // --- 重置所有节点 ---
  nodeSel
    .attr("fill", function () {
      return d3.select(this).attr("data-fill");
    })
    .attr("opacity", 1)
    .attr("stroke", "none")
    .attr("stroke-width", 0)
    .attr("filter", "url(#nodeShadow)"); // 恢复默认阴影

  if (!selectedId) return; // 没有选中节点，直接返回

  // --- 找出相连的边 ---
  const connectedLinks = linkSel.filter(
    l => l.source.id === selectedId || l.target.id === selectedId
  )
    .attr("stroke", function (d) {
      const base = d3.color(d3.select(this).attr("data-stroke")) || d3.rgb(30, 144, 255);
      const glowColor = d3.interpolateRgb(base, d3.rgb(80, 180, 255))(0.7);
      return glowColor;
    })
    .attr("stroke-width", d => 1 + 20 * Math.pow((d.weight - 0.7) / 0.3, 3))
    .attr("opacity", 1)
    .attr("filter", d => {
      // 根据 weight 调整发光强度
      const glowId = `edgeGlow-${Math.round(d.weight * 1000)}`;
      if (svg.select(`#${glowId}`).empty()) {
        const f = svg.select("defs").append("filter")
          .attr("id", glowId)
          .attr("x", "-50%")
          .attr("y", "-50%")
          .attr("width", "200%")
          .attr("height", "200%");
        f.append("feGaussianBlur")
          .attr("in", "SourceGraphic")
          .attr("stdDeviation", 3 + 5 * d.weight)
          .attr("result", "blur");
        const merge = f.append("feMerge");
        merge.append("feMergeNode").attr("in", "blur");
        merge.append("feMergeNode").attr("in", "SourceGraphic");
      }
      return `url(#${glowId})`;
    });

  // --- 收集相邻节点 ---
  const neighborIds = new Set([selectedId]);
  connectedLinks.each(l => {
    neighborIds.add(l.source.id);
    neighborIds.add(l.target.id);
  });

  // --- 高亮节点 ---
  nodeSel
    .attr("opacity", d => (neighborIds.has(d.id) ? 1 : 0.2))
    .attr("stroke", d => (d.id === selectedId ? "#FFD700" : "#1e90ff"))
    .attr("stroke-width", d => (d.id === selectedId ? 6 : (neighborIds.has(d.id) ? 3 : 0)))
    .attr("filter", d => (neighborIds.has(d.id) ? "url(#nodeGlow)" : "url(#nodeShadow)"));
}

// 智能居中函数
const smartCenterGraph = () => {
  if (!g || !svg || !graphContainer.value) return;
  requestAnimationFrame(() => {
    const container = graphContainer.value;
    try {
      const bbox = g.node().getBBox();
      if (!bbox || !isFinite(bbox.width) || !isFinite(bbox.height) ||
        bbox.width === 0 || bbox.height === 0) return;

      const leftOffset = 450;
      const rightOffset = 400;
      const topOffset = 50;
      const visibleWidth = Math.max(10, container.clientWidth - leftOffset - rightOffset);
      const visibleHeight = Math.max(10, container.clientHeight - topOffset);

      const scale = Math.min(1, Math.max(0.2, 0.8 / Math.max(
        bbox.width / visibleWidth,
        bbox.height / visibleHeight
      )));
      const translate = [
        leftOffset + visibleWidth / 2 - scale * (bbox.x + bbox.width / 2),
        topOffset + visibleHeight / 2 - scale * (bbox.y + bbox.height / 2)
      ];
      svg.transition().duration(800)
        .call(zoom.transform, d3.zoomIdentity.translate(translate[0], translate[1]).scale(scale));
    } catch (e) {
      if (debugMode.value)
        console.warn("Centergraph Failed!", e);
    }
  });
};


watch(activeCategories, () => {
  if (debugMode.value)
    console.log('activeCategories 变化了:', Array.from(activeCategories.value));
  updateNodeRings();
}, { deep: true });

const renderGraph = async () => {
  await initGraph();
  updateGraphData();
  setTimeout(() => smartCenterGraph(), 1500);

  if (resizeHandler) window.removeEventListener("resize", resizeHandler);
  resizeHandler = () => requestAnimationFrame(smartCenterGraph);
  window.addEventListener("resize", resizeHandler);
};

onMounted(renderGraph);

watch(() => [props.nodes, props.links], async () => {
  if (!svg) renderGraph();
  else {
    updateGraphData();
    await nextTick();
    updateHighlight(); // ✅ 确保新节点也有正确的高亮/淡化状态
    setTimeout(() => smartCenterGraph(), 1000);
  }
}, { deep: true });


onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener("resize", resizeHandler);
  if (simulation) {
    simulation.stop();
    simulation = null;
  }
});

watch(() => props.threshold, () => {
  if (simulation) updateGraphData();
});




const activateCommunityLayout = () => {
  if (!simulation || !props.nodes?.length) return;

  const container = graphContainer.value;
  const width = container.clientWidth;
  const height = container.clientHeight;
  const centerX = width / 2;
  const centerY = height / 2;

  // === 获取社区及其数量 ===
  const communities = Array.from(new Set(props.nodes.map(n => n.community_id).filter(Boolean)));
  const numCommunities = Math.max(1, communities.length);

  // === 社区在圆周上分布 ===
  const layoutRadius = Math.min(width, height) * 0.4;
  const angleStep = (2 * Math.PI) / numCommunities;
  const communityCenters = new Map();
  communities.forEach((cid, i) => {
    const angle = i * angleStep;
    communityCenters.set(cid, {
      x: centerX + layoutRadius * Math.cos(angle),
      y: centerY + layoutRadius * Math.sin(angle)
    });
  });

  // === 替换力系统 ===
  simulation
    .force("center", null)
    .force("gravity", null)
    .force("communityX", d3.forceX(d => {
      const c = communityCenters.get(d.community_id);
      return c ? c.x : centerX;
    }).strength(0.4))
    .force("communityY", d3.forceY(d => {
      const c = communityCenters.get(d.community_id);
      return c ? c.y : centerY;
    }).strength(0.4))
    .force("charge", d3.forceManyBody().strength(-120))
    .force("collision", d3.forceCollide()
      .radius(d => (nodeRadiusScale ? nodeRadiusScale(d.relevance) : 40) * 3.5 + 10)
      .strength(0.01)
    );

  // === 社区间排斥 ===
  const communityRepelForce = () => {
    const nodes = simulation.nodes();
    const K = 1000;
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        if (!a.community_id || !b.community_id) continue;
        if (a.community_id === b.community_id) continue;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const dist2 = dx * dx + dy * dy + 1;
        const f = K / dist2;
        a.vx -= f * dx;
        a.vy -= f * dy;
        b.vx += f * dx;
        b.vy += f * dy;
      }
    }
  };
  simulation.force("communityRepel", communityRepelForce);

  // === 调整链接强度（同社区更紧） ===
  const linkForce = simulation.force("link");
  if (linkForce) {
    linkForce.strength(d =>
      (d.source.community_id === d.target.community_id ? 0.3 : 0.02)
    );
  }

  simulation.alpha(0.1).alphaDecay(0.00002).restart();
  setTimeout(() => smartCenterGraph(), 1000);
};



// 恢复默认布局
const resetToDefaultLayout = () => {
  if (debugMode.value)
    console.log('🔄 恢复默认布局');
  if (!simulation || !props.nodes?.length || !graphContainer.value) return;

  const container = graphContainer.value;
  const width = container.clientWidth;
  const height = container.clientHeight;
  const centerX = width / 2;
  const centerY = height / 2;

  simulation
    .force("center", d3.forceCenter(centerX, centerY))
    .force("gravity", d3.forceRadial(
      Math.min(centerX, centerY) * 0.3,
      centerX,
      centerY
    ).strength(0.005))
    .force("charge", d3.forceManyBody().strength(d => {

      const connectedLinks = props.links.filter(
        l => l.source.id === d.id || l.target.id === d.id
      );
      const maxWeight = connectedLinks.length > 0
        ? Math.max(...connectedLinks.map(l => l.weight))
        : 0;

      const baseStrength = -90;
      const minStrength = -50;
      const adjusted = baseStrength * (1 - 0.5 * maxWeight);
      return Math.max(minStrength, adjusted);
    }))
    .force("collision", d3.forceCollide()
      .radius(d => (nodeRadiusScale ? nodeRadiusScale(d.relevance) : 40) + 10)
      .strength(1)
    )
    .force("communityX", null)
    .force("communityY", null)
    .force("communityRepel", null);

  // === 恢复链接力 ===
  const linkForce = simulation.force("link");
  if (linkForce) {
    linkForce
      .id(d => d.id)
      .distance(d => {
        const threshold = props.threshold;
        const baseDistance = 400 - (threshold * 150);
        const weightFactor = 0.3 + (1 - d.weight) * 4;
        const calculatedDistance = baseDistance * weightFactor;
        const minDistance = 100;
        const maxDistance = 5000;
        return Math.max(minDistance, Math.min(calculatedDistance, maxDistance));
      })
      .strength(d => 0.1 + 0.01 * d.weight);
  }

  // === 恢复初始的 alpha 参数 ===
  simulation
    .alpha(2)
    .alphaDecay(0.00002)
  simulation.restart();

  // === 重新居中 ===
  setTimeout(() => smartCenterGraph(), 800);
};


watch(() => props.sparkMode, (newVal) => {
  if (debugMode.value)
    console.log('Graph.vue - Spark 模式变化:', newVal)
  if (newVal) {
    activateCommunityLayout()
  } else {
    resetToDefaultLayout()
  }
})

defineExpose({
  smartCenterGraph,

});

</script>

<style scoped>
.graph-container {
  background-color: rgb(241, 240, 240);
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;
}
</style>
