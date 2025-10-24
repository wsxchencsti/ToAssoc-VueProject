<template>
  <transition name="spark-confirm-fade">
    <div v-if="visible" class="spark-confirm-overlay" @click.self="handleCancel">
      <div class="spark-confirm-popup">
        <div class="spark-confirm-header">
          <h3>启用 SparkClustering</h3>
          <button class="close-btn" @click="handleCancel">
            <img src="/icons/close.svg" alt="close">
          </button>
        </div>

        <div class="spark-confirm-content">
          <div class="spark-icon">
            <img src="/icons/flower_active.svg" alt="spark">
          </div>

          <div class="spark-description">
            <h3>SparkClustering</h3>
            <div class="tech-stack">Powered by Spark • FAISS • Louvain</div>
            <div class="cloud-support">Hosted on Alibaba Cloud</div>
            <ul>
              <li>基于语义对<span style="color: orangered;">目前展示的</span>论文进行社群划分</li>
              <li>自动形成结点簇</li>
              <li style=" color: red;">开启时禁用筛选项</li>
            </ul>
            <p class="spark-tip"></p>
          </div>
        </div>

        <div class="spark-confirm-actions">
          <button class="confirm-btn confirm-primary" @click="handleConfirm">启用</button>
          <button class="confirm-btn cancel-btn" @click="handleCancel">返回</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])

const handleConfirm = () => {
  emit('confirm')
}

const handleCancel = () => {
  emit('cancel')
}
</script>

<style scoped>
.spark-confirm-fade-enter-active,
.spark-confirm-fade-leave-active {
  transition: all 0.3s ease;
}

.spark-confirm-fade-enter-from,
.spark-confirm-fade-leave-to {
  opacity: 0;
  transform: scale(0.9);
  transition: all 0.0s ease;
}

.spark-confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
}

.spark-confirm-popup {
  background: #ffffff;
  border-radius: 12px;
  width: 380px;
  max-width: 90vw;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.spark-confirm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(90deg, #ff6b6b, #ff8e53);
  padding: 16px 20px;
  color: white;
}

.spark-confirm-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
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
  /* 继承按钮颜色 */
}

.close-btn:hover {
  background-color: rgba(255, 255, 255, 0.15);
  transform: scale(1.05);
}

.spark-confirm-content {
  padding: 24px;
  text-align: center;
}

.spark-icon {
  margin-bottom: 16px;
}

.spark-icon img {
  width: 48px;
  height: 48px;
}

.spark-description h3 {
  margin: -20px 0 1px 0;
  color: #333;
  font-size: 1.5rem;
}

.spark-description ul {
  text-align: left;
  margin: 0 0 16px 0;
  padding-left: 16px;
  color: #666;
  line-height: 1.5;
}

.spark-description li {
  margin-bottom: 6px;
}

.spark-tip {
  color: #ff6b6b;
  font-weight: 500;
  margin: 0;
}

.spark-confirm-actions {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  background: #f8f9fa;
  border-top: 1px solid #e9ecef;
}

.confirm-btn {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cancel-btn {
  background: #6c757d;
  color: white;
}

.cancel-btn:hover {
  background: #5a6268;
}

.confirm-primary {
  background: linear-gradient(90deg, #ff6b6b, #ff8e53);
  color: white;
}

.confirm-primary:hover {
  background: linear-gradient(90deg, #ff5252, #ff7b3a);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.3);
}

.tech-stack,
.cloud-support {
  font-size: 0.8rem;
  color: #666;
  margin-top: 1px;
  font-family: 'Courier New', 'SF Mono', 'Monaco', monospace;
  letter-spacing: -0.1px;
  font-weight: 400;
}
</style>