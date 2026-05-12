<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { woPage } from '@/api/cmms'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const list = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const hasMore = ref(true)
/** 与下拉项 value 一致，便于 van-dropdown-item 绑定 */
const scope = ref('assign')

const statusMap: Record<number, string> = {
  0: '待处理',
  1: '处理中',
  2: '待验收',
  3: '完成',
  4: '取消'
}

async function fetchPage(reset: boolean) {
  if (reset) {
    page.value = 1
    list.value = []
    hasMore.value = true
  }
  if (!hasMore.value) {
    loading.value = false
    refreshing.value = false
    return
  }
  loading.value = true
  try {
    const uid = user.profile?.id
    const body: Record<string, unknown> = { page: page.value, size: 12 }
    if (scope.value === 'assign' && uid) body.assignUserId = uid
    if (scope.value === 'report' && uid) body.reportUserId = uid
    const res: any = await woPage(body)
    const records = (res.data.records || []) as Record<string, unknown>[]
    const total = Number(res.data.total || 0)
    list.value.push(...records)
    if (list.value.length >= total) hasMore.value = false
    else page.value += 1
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => fetchPage(true))

watch(scope, () => fetchPage(true))

function onLoad() {
  if (!loading.value && hasMore.value) void fetchPage(false)
}
</script>

<template>
  <div>
    <van-nav-bar title="维修工单" fixed placeholder />
    <van-dropdown-menu>
      <van-dropdown-item
        v-model="scope"
        :options="[
          { text: '指派给我', value: 'assign' },
          { text: '我上报的', value: 'report' },
          { text: '全部', value: 'all' }
        ]"
      />
    </van-dropdown-menu>
    <van-pull-refresh v-model="refreshing" @refresh="fetchPage(true)">
      <van-list :loading="loading" :finished="!hasMore" finished-text="没有更多了" @load="onLoad">
        <van-skeleton title :row="2" :loading="loading && list.length === 0">
          <van-cell
            v-for="w in list"
            :key="String(w.id)"
            :title="String(w.orderCode)"
            :label="`${statusMap[Number(w.status)] ?? '-'} · 设备 ${w.deviceId}`"
            is-link
            @click="router.push(`/wo/${w.id}`)"
          />
          <van-empty v-if="!loading && !list.length" description="暂无工单" />
        </van-skeleton>
      </van-list>
    </van-pull-refresh>
  </div>
</template>
