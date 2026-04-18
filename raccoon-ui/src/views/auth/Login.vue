<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginForm = ref({
  username: '',
  password: '',
  remember: false
})

const loading = ref(false)

// 从localStorage加载记住的密码
onMounted(() => {
  const savedUser = localStorage.getItem('rememberedUser')
  if (savedUser) {
    const user = JSON.parse(savedUser)
    loginForm.value.username = user.username
    loginForm.value.password = user.password
    loginForm.value.remember = true
  }
})

const handleLogin = async () => {
  // 表单验证
  if (!loginForm.value.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (loginForm.value.password.length < 6) {
    ElMessage.warning('密码长度至少6位')
    return
  }

  loading.value = true
  try {
    await userStore.login(loginForm.value.username, loginForm.value.password)
    ElMessage.success('登录成功')

    // 记住密码
    if (loginForm.value.remember) {
      localStorage.setItem('rememberedUser', JSON.stringify({
        username: loginForm.value.username,
        password: loginForm.value.password
      }))
    } else {
      localStorage.removeItem('rememberedUser')
    }

    // 回跳逻辑
    const redirect = route.query.redirect as string
    if (redirect) {
      router.push(redirect)
    } else {
      router.push('/')
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">
          <img src="@/assets/logo.svg" alt="Raccoon Inspection" />
        </div>
        <h2>Raccoon Inspection</h2>
        <p>智能巡检管理系统</p>
      </div>
      <el-form :model="loginForm" class="login-form">
        <el-form-item>
          <el-input
            v-model="loginForm.username"
            placeholder="用户名/手机号"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item class="login-remember">
          <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
          <el-link type="primary" href="#" style="float: right">忘记密码?</el-link>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
        <el-form-item class="login-register">
          <span>还没有账号?</span>
          <el-link type="primary" href="/register">立即注册</el-link>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-box {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo {
  margin-bottom: 16px;
}

.logo img {
  width: 64px;
  height: 64px;
}

.login-header h2 {
  color: #333;
  margin-bottom: 8px;
}

.login-header p {
  color: #666;
  font-size: 14px;
}

.login-remember {
  margin-bottom: 20px;
}

.login-register {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .login-box {
    padding: 30px 20px;
  }
  
  .login-header h2 {
    font-size: 20px;
  }
  
  .logo img {
    width: 48px;
    height: 48px;
  }
}
</style>