<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
  agree: false
})

const loading = ref(false)

// 表单验证
const validateForm = () => {
  if (!registerForm.username) {
    ElMessage.warning('请输入用户名')
    return false
  }
  if (registerForm.username.length < 3 || registerForm.username.length > 20) {
    ElMessage.warning('用户名长度必须在3-20位之间')
    return false
  }
  if (!registerForm.password) {
    ElMessage.warning('请输入密码')
    return false
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码长度至少6位')
    return false
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  if (!registerForm.phone) {
    ElMessage.warning('请输入手机号')
    return false
  }
  if (!/^1[3-9]\d{9}$/.test(registerForm.phone)) {
    ElMessage.warning('手机号格式不正确')
    return false
  }
  if (!registerForm.email) {
    ElMessage.warning('请输入邮箱')
    return false
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) {
    ElMessage.warning('邮箱格式不正确')
    return false
  }
  if (!registerForm.agree) {
    ElMessage.warning('请同意隐私协议')
    return false
  }
  return true
}

// 注册
const handleRegister = async () => {
  if (!validateForm()) {
    return
  }
  loading.value = true
  try {
    await register(registerForm)
    ElMessage.success('注册成功')
    // 跳转到登录页
    router.push('/login')
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <div class="logo">
          <img src="@/assets/logo.svg" alt="Raccoon Inspection" />
        </div>
        <h2>Raccoon Inspection</h2>
        <p>智能巡检管理系统</p>
      </div>
      <el-form :model="registerForm" class="register-form">
        <el-form-item>
          <el-input
            v-model="registerForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            prefix-icon="Lock"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="registerForm.phone"
            placeholder="手机号"
            prefix-icon="Smartphone"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="registerForm.email"
            placeholder="邮箱"
            prefix-icon="Message"
            size="large"
          />
        </el-form-item>
        <el-form-item class="agree-item">
          <el-checkbox v-model="registerForm.agree">
            我已阅读并同意 <el-link type="primary" href="#">《用户协议》</el-link> 和 <el-link type="primary" href="#">《隐私政策》</el-link>
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
        <el-form-item class="login-link">
          <span>已有账号?</span>
          <el-link type="primary" href="/login">立即登录</el-link>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.register-box {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.register-header {
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

.register-header h2 {
  color: #333;
  margin-bottom: 8px;
}

.register-header p {
  color: #666;
  font-size: 14px;
}

.agree-item {
  margin-bottom: 20px;
}

.login-link {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .register-box {
    padding: 30px 20px;
  }
  
  .register-header h2 {
    font-size: 20px;
  }
  
  .logo img {
    width: 48px;
    height: 48px;
  }
}
</style>