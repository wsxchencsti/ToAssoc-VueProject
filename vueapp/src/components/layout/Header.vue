<!-- Header.vue -->
<template>
  <header class="header">
    <h1>ToAssoc</h1>
    <SearchBox v-if="showSearch" :loading="loading" @search="$emit('search', $event)" />
    <nav class="nav-buttons">
      <a href="https://github.com/wsxchencsti/ToAssoc-VueProject" target="_blank" rel="noopener noreferrer"
        class="link-btn">
        <!-- <img src="/icons/header/openinnew.svg" alt="open" class="open-icon" /> -->
        View on Github
      </a>
      <button @click="showPPT">Display</button>

      <img src="/icons/header/account.svg" alt="Account" class="account-icon" title="卖掉了" />
    </nav>
    <PPTPlayer ref="pptRef" />
  </header>
</template>

<script setup>
import SearchBox from '../utils/SearchBox.vue';
import PPTPlayer from '../utils/PPTPlayer.vue';
import { ref } from 'vue';

defineProps({
  loading: Boolean, showSearch: Boolean
});

defineEmits(['search']);
const pptRef = ref(null);
function showPPT() {
  pptRef.value.open(); // 调用子组件的 open() 方法
}
</script>

<style scoped>
.header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 50px;
  background-color: #ffffff;
  color: #296a5f;
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 左右分布 */
  padding: 0px;
  z-index: 2000;
  border-top: 2px solid #2a8878;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}



/* 按钮组 */
.nav-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 60px;
  padding-top: 2px;
}

.nav-buttons button,
.nav-buttons a {
  background-color: #fff;
  color: #2a8878;
  border: none;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.2s;
  text-decoration: none;
  outline: none;
}



.nav-buttons button:hover,
.nav-buttons a:hover {
  transform: scale(1.02);
}

.account-icon {
  margin-left: 20px;
  width: 32px;
  height: 32px;
  transition: transform 0.2s ease;
}

.open-icon {
  width: 20px;
  height: 20px;
  position: relative;
  top: 5px;
  margin-right: 1px;
}

h1 {
  padding-left: 20px;
  font-size: 1.5rem;
  margin: 0;
  white-space: nowrap;
}

.search-wrapper {
  margin-left: -10px;
}
</style>
