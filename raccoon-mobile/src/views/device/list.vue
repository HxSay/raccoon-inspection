<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { devicePage, deviceByCode } from '@/api/cmms'
import { scanDeviceCode } from '@/utils/scanner'

const router = useRouter()
const list = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const hasMore = ref(true)

async function fetchPage(reset: boolean) {
  if (reset) {
    page.value = 1
    list.value = []
    hasMore.value = true
  }
  if (!hasMore.value) return
  loading.value = true
  try {
    const res: any = await devicePage({ page: page.value, size: 15 })
    const records = res.data.records as Record<string, unknown>[]
    const total = res.data.total as number
    list.value.push(...records)
    if (list.value.length >= total) hasMore.value = false
    else page.value += 1
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => fetchPage(true))

async function onScan() {
  const code = await scanDeviceCode()
  if (!code) return
  try {
    const res: any = await deviceByCode(code)
    if (!res.data?.id) {
      showToast('未找到设备')
      return
    }
    router.push(`/device/${res.data.id}`)
  } catch {
    showToast('查询失败')
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="设备台账" fixed placeholder>
      <template #right>
        <van-icon name="scan" size="20" @click="onScan" />
      </template>
    </van-nav-bar>
    <van-pull-refresh v-model="refreshing" @refresh="fetchPage(true)">
      <van-skeleton title :row="4" :loading="loading && list.length === 0">
        <van-cell
          v-for="d in list"
          :key="String(d.id)"
          :title="String(d.deviceName)"
          :label="String(d.deviceCode)"
          is-link
          @click="router.push(`/device/${d.id}`)"
        />
        <van-empty v-if="!loading && !list.length" description="暂无设备" />
      </van-skeleton>
      <div class="more">
        <van-button v-if="hasMore" size="small" :loading="loading" @click="fetchPage(false)">加载更多</van-button>
      </div>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.more {
  text-align: center;
  padding: 12px;
}
</style>
