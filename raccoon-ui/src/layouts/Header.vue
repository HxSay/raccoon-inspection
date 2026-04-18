<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

defineProps<{
  collapse: boolean
}>()

defineEmits<{
  (e: 'toggle-collapse'): void
}>()

const userStore = useUserStore()
const router = useRouter()

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await userStore.logout()
      router.push('/login')
    } catch {
      // user cancel
    }
  }
}
</script>

<template>
  <div class="header-content">
    <div class="left">
      <el-icon
        class="collapse-icon"
        @click="$emit('toggle-collapse')"
      >
        <Fold v-if="!collapse" />
        <Expand v-else />
      </el-icon>
    </div>
    <div class="right">
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          <el-avatar :size="32" style="margin-right: 8px">
            {{ userStore.userInfo?.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <span class="username">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
          <el-icon class="el-icon--right">
            <ArrowDown />
          </el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left {
  display: flex;
  align-items: center;
}

.collapse-icon {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.collapse-icon:hover {
  color: #409EFF;
}

.right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  color: #606266;
  font-size: 14px;
}
</style>