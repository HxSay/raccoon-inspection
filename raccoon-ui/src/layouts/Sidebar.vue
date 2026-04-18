<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineProps<{
  collapse: boolean
}>()

const route = useRoute()
const router = useRouter()

const menuList = computed(() => {
  const children = router.getRoutes().find(r => r.path === '/')?.children || []
  return children.filter(item => item.meta && item.meta.title)
})

const activeMenu = computed(() => {
  return route.path
})
</script>

<template>
  <div class="sidebar">
    <div class="logo">
      <img src="@/assets/logo.svg" alt="logo" class="logo-img" />
      <span v-if="!collapse" class="logo-text">Raccoon</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="collapse"
      :collapse-transition="false"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <el-menu-item
        v-for="item in menuList"
        :key="item.path"
        :index="item.path"
      >
        <el-icon v-if="item.meta?.icon">
          <component :is="item.meta.icon" />
        </el-icon>
        <template #title>
          {{ item.meta?.title }}
        </template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4a;
}

.logo-img {
  width: 32px;
  height: 32px;
}

.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  margin-left: 8px;
}

.el-menu {
  flex: 1;
  border-right: none;
}
</style>