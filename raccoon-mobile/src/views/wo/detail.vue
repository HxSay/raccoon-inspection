<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { woGet, woSave, woStatus } from '@/api/cmms'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const row = ref<Record<string, unknown> | null>(null)
const loading = ref(true)
const repairContent = ref('')
const saving = ref(false)

const statusMap: Record<number, string> = {
  0: '待处理',
  1: '处理中',
  2: '待验收',
  3: '完成',
  4: '取消'
}

async function load() {
  loading.value = true
  try {
    const res: any = await woGet(id)
    row.value = res.data as Record<string, unknown>
    repairContent.value = String(row.value?.repairContent ?? '')
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 接单：0 → 1 */
async function accept() {
  try {
    await showConfirmDialog({ title: '接单', message: '确认开始处理该工单？' })
    await woStatus(id, 1)
    showToast('已开始处理')
    load()
  } catch {
    /* cancel */
  }
}

/** 保存维修内容并提交验收：1 → 2 */
async function submitCheck() {
  if (!row.value) return
  saving.value = true
  try {
    await woSave({
      ...row.value,
      repairContent: repairContent.value,
      id: row.value.id,
      deviceId: row.value.deviceId,
      orderCode: row.value.orderCode
    })
    await woStatus(id, 2)
    showToast('已提交验收')
    load()
  } finally {
    saving.value = false
  }
}

/** 验收通过：2 → 3 */
async function passAccept() {
  try {
    await showConfirmDialog({ title: '验收', message: '确认验收通过并关闭工单？' })
    await woStatus(id, 3)
    showToast('工单已完成')
    load()
  } catch {
    /* cancel */
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="工单详情" left-arrow fixed placeholder @click-left="router.back()" />
    <van-skeleton title :row="6" :loading="loading">
      <template v-if="row">
        <van-cell-group inset title="基本信息">
          <van-cell title="工单号" :value="String(row.orderCode)" />
          <van-cell title="设备 ID" :value="String(row.deviceId)" />
          <van-cell title="状态" :value="statusMap[Number(row.status)] ?? '-'" />
          <van-cell title="来源" :value="['-', '巡检', '手动', '预警'][Number(row.source)] || '-'" />
          <van-cell title="类型" :value="Number(row.orderType) === 2 ? '保养' : '维修'" />
          <van-cell title="故障描述" :label="String(row.faultDescription || '-')" />
        </van-cell-group>
        <van-cell-group inset title="维修处理" style="margin-top: 12px">
          <van-field
            v-model="repairContent"
            rows="3"
            autosize
            label="维修内容"
            type="textarea"
            maxlength="500"
            show-word-limit
            placeholder="对应 work_order.repair_content"
            :readonly="Number(row.status) >= 2"
          />
        </van-cell-group>
        <div class="actions">
          <van-button v-if="Number(row.status) === 0" type="primary" block round @click="accept">接单处理</van-button>
          <van-button
            v-if="Number(row.status) === 1"
            type="primary"
            block
            round
            :loading="saving"
            @click="submitCheck"
          >
            保存并提交验收
          </van-button>
          <van-button v-if="Number(row.status) === 2" type="success" block round @click="passAccept">验收通过</van-button>
        </div>
      </template>
      <van-empty v-else description="工单不存在" />
    </van-skeleton>
  </div>
</template>

<style scoped>
.actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
