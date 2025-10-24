<template>
  <div class="welcome-container">
    <!-- 🌿 左侧装饰SVG -->
    <img src="/icons/common/graph_decoration1.svg" alt="Decoration" class="left-decoration" />
    <img src="/icons/common/graph_decoration1.svg" alt="Decoration" class="right-decoration" />


    <div class="intro-info">
      <h1>Associate Your Query</h1>
      <p>Explore research proximity beyond existing links.</p>
    </div>
    <!-- ✅ 居中搜索框 -->
    <div class="search-overlay">
      <SearchBox :loading="loading" @search="onSearch" />
    </div>
    <!-- Three.js 场景 -->
    <div ref="sceneContainer" class="scene-container"></div>

  </div>
</template>

<script setup>
import * as THREE from "three";
import { onMounted, onBeforeUnmount, ref, nextTick } from "vue";
import SearchBox from "./SearchBox.vue";

const emit = defineEmits(["search"]);
const props = defineProps({
  loading: Boolean
});

// ✅ 搜索框触发时，向父组件发送事件
function onSearch(query) {
  emit("search", query);
}


const sceneContainer = ref(null);
let scene, camera, renderer;
let planets = [], orbitLines = [], animationId;
let tilt_set = Math.PI / 3.5;
let y_rotation = Math.PI / 5;
let x_rotation = Math.PI / 4;

// === 参数设置 ===
const config = {
  orbitRadius: 2.5,
  revolutionSpeed: 1.3,
};

// === 图标数组 ===
const icons = [
  "/icons/welcome/vue.svg",
  "/icons/welcome/D3js.svg",
  "/icons/welcome/Threejs.svg",
  "/icons/welcome/echarts.png"
];

const icons2 = [
  "/icons/welcome/FastAPI.svg",
  "/icons/welcome/PostgreSQL.svg",
  "/icons/welcome/apache_spark.svg",
  "/icons/welcome/aliyun.svg"
];
let mouseInside = false;

onMounted(() => {
  nextTick(() => {
    initScene();
    animate();
  });

  const container = sceneContainer.value;

  // ✅ 改成监听全局 mousemove，根据坐标判断是否在中心区域
  window.addEventListener("mousemove", (event) => {
    const rect = container.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height * 0.65; // 搜索框大约在 35% 位置
    const radius = Math.min(rect.width, rect.height) * 0.2; // 中心半径区域（可调）

    const dx = event.clientX - centerX;
    const dy = event.clientY - centerY;
    const distance = Math.sqrt(dx * dx + dy * dy);

    mouseInside = distance < radius; // ✅ 只有进入这个圆形范围才算“inside”
  });

  window.addEventListener("resize", onResize);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId);
  window.removeEventListener("resize", onResize);
  if (renderer) renderer.dispose();
});

function initScene() {
  if (!sceneContainer.value) return;

  const container = sceneContainer.value;
  const { width, height } = container.getBoundingClientRect();

  // === 场景 ===
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0xffffff);
  scene.position.y = -4.0;

  // === 摄像机 ===
  camera = new THREE.PerspectiveCamera(40, width / height, 0.1, 100);
  camera.position.set(10, 8, 1);
  camera.lookAt(0, -1, 0);

  // === 渲染器 ===
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setSize(width, height);
  container.appendChild(renderer.domElement);

  // === 光照 ===
  const ambientLight = new THREE.AmbientLight(0xffffff, 1);
  scene.add(ambientLight);

  // === 创建轨道线 ===
  createOrbitLines();
  createOrbitLines(true); // 第二轨道，镜像

  // === 创建行星 ===
  const loader = new THREE.TextureLoader();
  icons.forEach((icon, i) => createPlanet(loader, icon, i));
  icons2.forEach((icon, i) => createPlanet(loader, icon, i, true)); // 第二轨道，mirror = true

  window.addEventListener("resize", onResize);

}

// 创建轨道线
function createOrbitLines(mirror = false) {
  const layers = 4;
  const baseRadius = config.orbitRadius;
  const segments = 128;
  const colorFront = new THREE.Color(0x777777);
  const colorBack = new THREE.Color(0xcccccc);
  // if(mirror) baseRadius += 1;
  for (let l = 0; l < layers; l++) {
    const radiusOffset = (l - layers / 2) * 0.05;
    const radius = baseRadius + radiusOffset;
    const positions = [];
    const colors = [];

    for (let i = 0; i <= segments; i++) {
      const angle = (i / segments) * Math.PI * 2;
      const tilt = tilt_set;

      let x = Math.cos(angle) * radius;
      let y = Math.sin(angle) * radius * Math.cos(tilt);
      let z = Math.sin(angle) * radius * Math.sin(tilt);

      if (mirror) z = -z; // ✅ Y轴镜像

      positions.push(x, y, z);

      const t = (Math.sin(angle) + 1) / 2;
      let baseColor = colorBack.clone().lerp(colorFront, t);
      const fade = 0.5 + 1.5 * Math.sin(angle * 3 + l * 0.8 + Math.random() * 0.1) *
        (0.6 + 0.4 * Math.sin(angle * 5 + l));
      baseColor.multiplyScalar(fade);

      colors.push(baseColor.r, baseColor.g, baseColor.b);
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));

    const lineMaterial = new THREE.LineBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 0.5 - l * 0.1,
      linewidth: 1.5 + l * 0.5,
    });

    const line = new THREE.Line(geometry, lineMaterial);
    line.rotation.y = y_rotation;
    line.rotation.x = x_rotation;
    scene.add(line);
    orbitLines.push(line);
  }
}

// 创建单个行星
function createPlanet(loader, icon, i, mirror = false) {
  loader.load(icon, (texture) => {
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
    });

    const sprite = new THREE.Sprite(material);
    sprite.scale.set(0.8, 0.8, 1);
    sprite.renderOrder = 199;

    const arr = mirror ? icons2 : icons;
    const orbitAngle = (i / arr.length) * Math.PI * 2;
    const planet = {
      sprite,
      angle: orbitAngle,
      radius: config.orbitRadius,
      speed: config.revolutionSpeed,
      currentSpeed: config.revolutionSpeed, // 当前速度，会逐渐减慢
      mirror,
    };

    updatePlanetPosition(planet);
    planets.push(planet);
    scene.add(sprite);
  }, undefined, (err) => console.error("fail to load icon:", icon, err));
}

// 更新行星位置
function updatePlanetPosition(planet) {
  const tilt = tilt_set;

  // 基础椭圆轨道坐标
  let x = Math.cos(planet.angle) * planet.radius;
  let y = Math.sin(planet.angle) * planet.radius * Math.cos(tilt);
  let z = Math.sin(planet.angle) * planet.radius * Math.sin(tilt);

  if (planet.mirror) z = -z;

  // === 绕 Y 轴旋转 ===
  const rotatedX_Y = x * Math.cos(y_rotation) + z * Math.sin(y_rotation);
  const rotatedZ_Y = -x * Math.sin(y_rotation) + z * Math.cos(y_rotation);

  // === 绕 X 轴旋转 ===
  const rotatedY_X = y * Math.cos(x_rotation) - rotatedZ_Y * Math.sin(x_rotation);
  const rotatedZ_X = y * Math.sin(x_rotation) + rotatedZ_Y * Math.cos(x_rotation);

  planet.sprite.position.set(rotatedX_Y, rotatedY_X, rotatedZ_X);

  // === 根据相机距离计算夸张缩放 ===
  // 相机Z坐标
  const cameraZ = camera.position.z || 10;

  // 行星到摄像机的距离
  const distance = Math.max(0.1, cameraZ - rotatedZ_X); // 避免除以0

  // 自然透视缩放
  const scaleFactor = THREE.MathUtils.clamp(1.2 / Math.sqrt(distance), 0.5, 1.5);

  planet.sprite.scale.set(scaleFactor, scaleFactor, 1);
}


// 动画循环
function animate() {
  animationId = requestAnimationFrame(animate);

  // === 更新每个行星的运动 ===
  planets.forEach((p) => {
    if (mouseInside) {
      // 🟢 鼠标移上去时：缓缓加速到最高速（原速的 1.8 倍）
      const targetSpeed = config.revolutionSpeed * 0.1;
      p.currentSpeed += (targetSpeed - p.currentSpeed) * 0.5;
    } else {
      // 🟡 鼠标移开时：缓缓减速到最低速（原速的 0.3 倍）
      const targetSpeed = config.revolutionSpeed * 0.000001;
      p.currentSpeed += (targetSpeed - p.currentSpeed) * 0.05;
    }
    p.angle += p.currentSpeed;
    updatePlanetPosition(p);
  });

  renderer.render(scene, camera);
}


// 窗口缩放
function onResize() {
  if (!sceneContainer.value || !camera || !renderer) return;
  const { width, height } = sceneContainer.value.getBoundingClientRect();
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  renderer.setSize(width, height);
}
</script>

<style scoped>
.scene-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: #ffffff;
  z-index: 0;
}

.search-overlay {
  position: absolute;
  top: 35%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
  display: flex;
  justify-content: center;
  align-items: center;
  width: auto;
  pointer-events: none;
}

.search-overlay>* {
  pointer-events: auto;
}

.intro-info {
  position: absolute;
  top: 15%;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  z-index: 2;
  color: #052c25;
  pointer-events: none;
  transform: translateX(-50%) scaleY(1.05);
  /* 纵向拉伸 1.5 倍 */
}

.intro-info h1 {
  font-size: 2.5rem;
  margin: 0;
  font-weight: 700;
}

.intro-info p {
  font-size: 1.0rem;
  margin-top: 0.5rem;
  font-weight: 600;
  color: #052c25;
}

:deep(.search-box) {
  height: 46px;
}

:deep(.search-btn) {
  height: 40px;
  padding: 0 20px;
}

.left-decoration,
.right-decoration {
  position: fixed;
  /* ✅ 从 absolute 改为 fixed，不会撑页面 */
  top: 12%;
  height: 32vw;
  left: -12%;
  /* 👈 负值表示往屏幕外偏移一点，让它部分被截断 */
  width: 32vw;
  /* 相对屏幕宽度，响应式 */
  opacity: 0.4;
  /* 透明一点，避免喧宾夺主 */
  pointer-events: none;
  /* ✅ 不挡鼠标交互 */
  z-index: 1;
  /* 在场景前、在文字后 */
  transform: rotate(350deg);
  /* 可以加一点倾斜，更自然 */
}

.right-decoration {
  left: auto;
  right: -20%;
  height: 60vw;
  opacity: 0.8;
  transform: rotate(300deg);
}
</style>
