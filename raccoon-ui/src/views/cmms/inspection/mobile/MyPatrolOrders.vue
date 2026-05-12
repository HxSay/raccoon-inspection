<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { iwoMyPage, type IwoRow } from '@/api/inspectionWorkOrder'

const router = useRouter()

const tab = ref<'pending' | 'running' | 'done'>('pending')
const list = ref<IwoRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const shiftLabel = (v: number) =>
  ({ 1: '早班', 2: '中班', 3: '夜班' } as Record<number, string>)[v] ?? String(v)

const statusLabel = (s: number) =>
  (
    {
      1: '待下发',
      2: '待执行',
      3: '执行中',
      4: '已完成',
      5: '已取消'
    } as Record<number, string>
  )[s] ?? String(s)

const load = async () => {
  loading.value = true
  try {
    const res: any = await iwoMyPage({
      page: page.value,
      size: size.value,
      tab: tab.value
    })
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

watch(tab, () => {
  page.value = 1
  load()
})

onMounted(load)

const goExecute = (id: number) => {
  router.push(`/m/execute/${id}`)
}

const goDetail = (id: number) => {
  router.push(`/m/detail/${id}`)
}
</script>

<template>
  <div class="wrap" v-loading="loading">
    <el-alert type="info" :closable="false" show-icon class="tip">
      此处展示<strong>当前登录用户</strong>作为巡检人、且已下发的工单。派发后请在「待执行」中进入工单逐步填报。
    </el-alert>

    <el-segmented v-model="tab" :options="[
      { label: '待执行', value: 'pending' },
      { label: '执行中', value: 'running' },
      { label: '已完成', value: 'done' }
    ]" block class="tabs" />

    <div v-if="!list.length" class="empty">暂无数据</div>

    <div v-else class="cards">
      <el-card v-for="row in list" :key="row.id" class="card" shadow="hover">
        <div class="row line1">
          <span class="no">{{ row.orderNo }}</span>
          <el-tag size="small" type="info">{{ statusLabel(row.status) }}</el-tag>
        </div>
        <div class="row muted">{{ row.area }}</div>
        <div class="row small">
          <span>{{ shiftLabel(row.shiftType) }}</span>
          <span class="dot">·</span>
          <span>{{ row.planStartTime }} ~ {{ row.planEndTime }}</span>
        </div>
        <div class="actions">
          <el-button
            v-if="row.status === 2 || row.status === 3"
            type="primary"
            size="small"
            @click.stop="goExecute(row.id)"
            >去填报</el-button
          >
          <el-button v-if="row.status === 4" size="small" @click.stop="goDetail(row.id)">查看结果</el-button>
        </div>
      </el-card>
    </div>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      class="pager"
      :total="total"
      :page-sizes="[10, 20]"
      layout="total, prev, pager, next"
      small
      background
      @current-change="load"
      @size-change="load"
    />
  </div>
</template>

<style scoped>
.wrap {
  padding: 12px;
  max-width: 560px;
  margin: 0 auto;
}

.tip {
  margin-bottom: 12px;
}

.tabs {
  margin-bottom: 12px;
}

.empty {
  text-align: center;
  color: #909399;
  padding: 32px 0;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card {
  cursor: default;
}

.row {
  margin-bottom: 4px;
}

.line1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.no {
  font-weight: 600;
  font-size: 15px;
  word-break: break-all;
}

.muted {
  color: #606266;
  font-size: 14px;
}

.small {
  font-size: 12px;
  color: #909399;
}

.dot {
  margin: 0 4px;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
