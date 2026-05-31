<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPendingAudit } from '@/api/workOrderAudit'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const list = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const refreshing = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await listPendingAudit({
      inspectorId: user.profile?.id,
      page: 1,
      size: 50
    })
    list.value = (res.data?.records || []) as Record<string, unknown>[]
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => load())

function onRefresh() {
  refreshing.value = true
  void load()
}

function openDetail(id: number) {
  router.push(`/audit/${id}`)
}
</script>

<template>
  <div class="page">
    <van-nav-bar title="工单审核" left-arrow @click-left="router.back()" fixed placeholder />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="true">
        <van-empty v-if="!loading && !list.length" description="暂无待审核工单" />
        <van-cell-group v-else inset>
          <van-cell
            v-for="item in list"
            :key="String(item.id)"
            is-link
            :title="String(item.orderNo || '')"
            :label="`${item.area || ''} · 截止 ${item.auditDeadline || '—'}`"
            @click="openDetail(Number(item.id))"
          >
            <template #value>
              <van-tag type="warning">待审核</van-tag>
            </template>
          </van-cell>
        </van-cell-group>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: #f5f6f8;
}
</style>
