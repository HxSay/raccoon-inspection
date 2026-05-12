<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { taskPage } from '@/api/cmms'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const list = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const hasMore = ref(true)
/** 0 全部 1 待执行 2 执行中 3 已完成 */
const tabIdx = ref(0)

const statusText: Record<number, string> = { 0: '待执行', 1: '执行中', 2: '已完成', 3: '已过期' }

function mapStatus() {
  if (tabIdx.value === 0) return undefined
  return tabIdx.value - 1
}

async function fetchPage(reset: boolean) {
  if (reset) {
    page.value = 1
    list.value = []
    hasMore.value = true
  }
  if (!hasMore.value) return
  loading.value = true
  try {
    const uid = user.profile?.id
    const res: any = await taskPage({
      page: page.value,
      size: 15,
      execUserId: uid,
      status: mapStatus()
    })
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

watch(tabIdx, () => fetchPage(true))
</script>

<template>
  <div>
    <van-nav-bar title="巡检任务" fixed placeholder />
    <van-tabs v-model:active="tabIdx" shrink>
      <van-tab title="全部" />
      <van-tab title="待执行" />
      <van-tab title="执行中" />
      <van-tab title="已完成" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="fetchPage(true)">
      <van-skeleton title :row="3" :loading="loading && list.length === 0">
        <van-cell
          v-for="t in list"
          :key="String(t.id)"
          :title="String(t.taskName)"
          :label="`${t.taskCode} · ${statusText[Number(t.status)]}`"
          is-link
          @click="router.push(`/task/${t.id}`)"
        />
        <van-empty v-if="!loading && !list.length" description="暂无任务" />
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
