<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const stats = ref([
  { title: '用户总数', value: 0, icon: 'User', color: '#409EFF' },
  { title: '角色总数', value: 0, icon: 'UserFilled', color: '#67C23A' },
  { title: '在线设备', value: 0, icon: 'Monitor', color: '#E6A23C' },
  { title: '巡检任务', value: 0, icon: 'List', color: '#F56C6C' }
])
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col v-for="(stat, index) in stats" :key="index" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-title">{{ stat.title }}</p>
              <p class="stat-value">{{ stat.value }}</p>
            </div>
            <el-icon :size="48" :style="{ color: stat.color }">
              <component :is="stat.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>欢迎使用 Raccoon Inspection 系统</span>
          </template>
          <div class="welcome-content">
            <h3>您好，{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}！</h3>
            <p>欢迎使用智能巡检管理系统，系统运行正常。</p>
            <p style="margin-top: 16px; color: #909399">
              当前版本：v1.0.0 | 技术栈：Vue 3 + TypeScript + Element Plus
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" plain>新建巡检任务</el-button>
            <el-button type="success" plain>设备管理</el-button>
            <el-button type="warning" plain>数据统计</el-button>
            <el-button type="danger" plain>系统设置</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 0;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-title {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.welcome-content {
  padding: 20px 0;
}

.welcome-content h3 {
  margin-bottom: 12px;
  color: #303133;
}

.welcome-content p {
  color: #606266;
  line-height: 1.6;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-actions .el-button {
  width: 100%;
}
</style>