<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'

const isCollapse = ref(false)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}
</script>

<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="aside">
      <Sidebar :collapse="isCollapse" />
    </el-aside>
    <el-container>
      <el-header class="header">
        <Header :collapse="isCollapse" @toggle-collapse="toggleCollapse" />
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout {
  width: 100%;
  height: 100%;
}

.aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow-x: hidden;
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  padding: 0 16px;
}

.main {
  background-color: #f0f2f5;
  padding: 16px;
}
</style>