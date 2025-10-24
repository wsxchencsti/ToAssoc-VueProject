<!-- Header.vue -->
<template>
  <header class="header">
    <h1>我是标题我是标题我是标题</h1>
    <SearchBox v-if="showSearch" :loading="loading" @search="$emit('search', $event)" />
    <nav class="nav-buttons">
      <button @click="$emit('navigate', 'home')">View on Github</button>
      <button @click="$emit('navigate', 'about')">Display</button>
    </nav>
  </header>
</template>

<script setup>
import SearchBox from '../utils/SearchBox.vue';

defineProps({
  loading: Boolean, showSearch: Boolean
});

defineEmits(['search']);
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
  justify-content: space-between; /* 让左右分布 */
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
}

.nav-buttons button {
  background-color: #fff;
  color: #2a8878;
  border: none;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.2s;
  outline: none;
}

.nav-buttons button:hover {
  background-color: #ededed;
}

h1 {
  padding-left: 10px;
  font-size: 1.5rem;
  margin: 0;
  white-space: nowrap;
}
.search-wrapper{
  margin-left: -10px;
}
</style>
