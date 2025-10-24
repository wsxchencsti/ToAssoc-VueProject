<!-- SearchBox.vue -->
<template>
  <div class="search-wrapper">
    <div class="search-box">
      <input
        name="search-box-name"
        v-model="inputValue"
        @keyup.enter="onSearch"
        type="text"
        :placeholder="loading ? 'Loading...' : 'Search for topics...'"
        :disabled="loading"
      />
      <!-- 按钮内部加图标和文字 -->
      <button class="search-btn" @click="onSearch" :disabled="loading">
        <img src="/icons/search/search.svg" alt="search" />
        <span>Search</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const emit = defineEmits(['search']);
const props = defineProps({
  loading: Boolean
});

const inputValue = ref('');

const onSearch = () => {
  if (!props.loading && inputValue.value.trim()) {
    emit('search', inputValue.value.trim());
    inputValue.value = '';
  }
};
</script>

<style scoped>
.search-wrapper {
  flex: 0 0 auto;
  margin-left: 100px;
}

/* 搜索框外壳 */
.search-box {
  position: relative;
  display: flex;
  align-items: center;
  width: 900px;
  height: 36px;
  border: 1px solid #ccc;
  border-radius: 12px;
  overflow: visible;
}

/* 输入框 */
.search-box input {
  flex: 1;
  width: 100%;
  height: 100%;
  padding: 0 100px 0 10px; /* 右侧留出按钮空间 */
  border: none;
  outline: none;
  font-size: 1rem;
  border-radius: 12px;
}

/* 按钮 */
.search-btn {
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  height: 30px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 1px; /* 图标和文字间距 */
  border: none;
  border-radius: 12px; /* 按钮圆角 */
  background-color: #2a8878;
  color: white;
  font-size: 0.85rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

/* 图标 */
.search-btn img {
  width: 16px;
  height: 16px;
  object-fit: contain; /* 保持 SVG 比例 */
}

/* hover 效果 */
:deep(.search-btn:hover) {
  background-color: #246d60;
}

/* focus 效果 */
:deep(.search-box input:focus) {
  outline: none;
  box-shadow: 0 0 5px rgba(42, 136, 120, 0.4);
}

/* disabled 状态 */
.search-box input:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.search-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}
</style>
