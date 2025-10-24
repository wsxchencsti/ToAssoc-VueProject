<!-- components/YearScale.vue -->
<template>
  <div class="year-scale-container">
    <div class="year-scale" ref="scaleContainer"></div>
  </div>
</template>

<script setup>
import * as d3 from "d3";
import { ref, onMounted, nextTick } from "vue";

const scaleContainer = ref(null);
let svg = null;
let g = null;

const minYear = 2005;
const maxYear = 2025;
const width = 250;
const height = 50;
const padding = 15; // 两边间距

const initScale = async () => {
  await nextTick();
  if (!scaleContainer.value) return;

  d3.select(scaleContainer.value).selectAll("*").remove();

  svg = d3.select(scaleContainer.value)
    .append("svg")
    .attr("width", width)
    .attr("height", height);

  g = svg.append("g").attr("transform", `translate(${padding}, 10)`);

  // 颜色比例尺
  const colorScale = d3.scaleLinear()
    .domain([minYear, maxYear])
    .range(["rgb(90, 166, 155)", "rgb(3, 44, 38)"]);

  // x 轴刻度比例尺
  const xScale = d3.scaleLinear()
    .domain([minYear, maxYear])
    .range([0, width - padding * 2]);

  // 渐变
  const gradient = g.append("defs")
    .append("linearGradient")
    .attr("id", "year-gradient")
    .attr("x1", "0%").attr("y1", "0%")
    .attr("x2", "100%").attr("y2", "0%");

  gradient.append("stop").attr("offset", "0%").attr("stop-color", colorScale(minYear));
  gradient.append("stop").attr("offset", "100%").attr("stop-color", colorScale(maxYear));

  // 渐变矩形
  g.append("rect")
    .attr("x", 0)
    .attr("y", 0)
    .attr("width", width - padding * 2)
    .attr("height", 12)
    .style("fill", "url(#year-gradient)")
    .attr("rx", 3)
    .attr("ry", 3)
    .style("fill-opacity", 0.7);

  // 刻度
  const tickCount = 5;
  const ticks = xScale.ticks(tickCount);

  g.selectAll(".tick-line")
    .data(ticks)
    .enter()
    .append("line")
    .attr("class", "tick-line")
    .attr("x1", d => xScale(d))
    .attr("x2", d => xScale(d))
    .attr("y1", 12)
    .attr("y2", 17)
    .attr("stroke", "#333")
    .attr("stroke-width", 1);

  g.selectAll(".tick-label")
    .data(ticks)
    .enter()
    .append("text")
    .attr("class", "tick-label")
    .attr("x", d => xScale(d))
    .attr("y", 32)
    .attr("text-anchor", "middle")
    .attr("font-size", "10px")
    .attr("fill", "#333")
    .text(d => d);
};

onMounted(async () => {
  await initScale();
});
</script>

<style scoped>
.year-scale-container {
  display: flex;
  justify-content: center;
}
</style>
