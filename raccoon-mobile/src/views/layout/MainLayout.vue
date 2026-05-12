<script setup lang="ts"></script>

<template>
  <div class="layout">
    <div class="layout-body">
      <router-view />
    </div>
    <van-tabbar route fixed placeholder safe-area-inset-bottom>
      <van-tabbar-item replace to="/home" icon="wap-home-o">工作台</van-tabbar-item>
      <van-tabbar-item replace to="/device" icon="cluster-o">设备</van-tabbar-item>
      <van-tabbar-item replace to="/task" icon="todo-list-o">任务</van-tabbar-item>
      <van-tabbar-item replace to="/wo" icon="setting-o">工单</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.layout-body {
  flex: 1;
  padding-bottom: 56px;
  overflow-y: auto;
}
</style>
