<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value || !password.value) {
    showToast('请输入账号密码')
    return
  }
  loading.value = true
  try {
    const res: any = await login(username.value, password.value)
    const u = res.data.user
    userStore.setSession(res.data.token, res.data.refreshToken, {
      id: u.id,
      username: u.username,
      nickname: u.nickname,
      userType: u.userType
    })
    showToast('登录成功')
    const redir = (route.query.redirect as string) || '/home'
    router.replace(redir)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="brand">
      <div class="logo">RI</div>
      <h1>智能巡检</h1>
      <p class="sub">现场作业 · 移动闭环</p>
    </div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field v-model="username" label="账号" placeholder="用户名/手机号" clearable />
        <van-field v-model="password" type="password" label="密码" placeholder="密码" clearable />
      </van-cell-group>
      <div class="btn-wrap">
        <van-button round block type="primary" native-type="submit" :loading="loading">登录</van-button>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.login {
  padding: 48px 16px;
  min-height: 100vh;
  background: linear-gradient(160deg, #1a1a2e 0%, #16213e 40%, #f4f6f8 40%);
}
.brand {
  text-align: center;
  color: #fff;
  margin-bottom: 36px;
}
.logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  background: #0f3460;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: 1px;
}
h1 {
  margin: 0;
  font-size: 22px;
}
.sub {
  margin: 8px 0 0;
  opacity: 0.85;
  font-size: 13px;
}
.btn-wrap {
  margin: 24px 16px 0;
}
</style>
