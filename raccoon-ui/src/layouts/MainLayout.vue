<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'

const route = useRoute()
const fullBleed = computed(() => Boolean(route.meta.fullBleed))

const isCollapse = ref(false)
/** 进入仿真全屏时暂存侧栏展开状态，离开后恢复 */
const collapseBeforeImmersive = ref(false)

const sidebarCollapsed = computed(() => fullBleed.value || isCollapse.value)
/** 仿真沉浸模式：侧栏完全收起（不占宽度） */
const asideWidth = computed(() => {
  if (fullBleed.value) return '0px'
  return sidebarCollapsed.value ? '64px' : '200px'
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

watch(
  fullBleed,
  (immersive, wasImmersive) => {
    if (immersive && !wasImmersive) {
      collapseBeforeImmersive.value = isCollapse.value
      isCollapse.value = true
    } else if (!immersive && wasImmersive) {
      isCollapse.value = collapseBeforeImmersive.value
    }
  },
  { immediate: true }
)
</script>

<template>
  <el-container class="main-layout" :class="{ 'main-layout--immersive': fullBleed }">
    <el-aside
      v-show="!fullBleed"
      :width="asideWidth"
      class="aside"
      :class="{ 'aside--collapsed': sidebarCollapsed }"
    >
      <Sidebar :collapse="sidebarCollapsed" />
    </el-aside>
    <el-container class="main-body">
      <el-header v-if="!fullBleed" class="header">
        <Header :collapse="isCollapse" @toggle-collapse="toggleCollapse" />
      </el-header>
      <el-main class="main" :class="{ 'main--bleed': fullBleed }">
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

.main-layout--immersive .aside {
  overflow: hidden;
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

.main-layout--immersive .main-body {
  width: 100%;
}

.main--bleed {
  padding: 0;
  height: 100vh;
  overflow: hidden;
  background: #0b1220;
  display: flex;
  flex-direction: column;
}

/* router-view 子页面（仿真全屏）撑满主区域 */
.main--bleed > :deep(> *) {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
}
</style>