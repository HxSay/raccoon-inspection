<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { approveAudit, getAuditDetail, rejectAudit, type WorkOrderAuditDetail } from '@/api/workOrderAudit'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const workOrderId = Number(route.params.id)
const detail = ref<WorkOrderAuditDetail | null>(null)
const rejectReason = ref('')
const showReject = ref(false)
const busy = ref(false)

async function load() {
  const res: any = await getAuditDetail(workOrderId)
  detail.value = res.data
}

onMounted(() => {
  if (!workOrderId) {
    showToast('无效工单')
    router.back()
    return
  }
  void load()
})

async function onApprove() {
  await showConfirmDialog({ title: '审核通过', message: '确认通过并正式下发巡检任务？' })
  busy.value = true
  try {
    await approveAudit({
      workOrderId,
      auditorId: user.profile?.id,
      auditorName: user.profile?.nickname || user.profile?.username
    })
    showToast('审核通过')
    router.replace('/audit')
  } catch (e: any) {
    showToast(e?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

async function onReject() {
  if (!rejectReason.value.trim()) {
    showToast('请填写驳回原因')
    return
  }
  busy.value = true
  try {
    await rejectAudit({
      workOrderId,
      rejectReason: rejectReason.value.trim(),
      auditorId: user.profile?.id,
      auditorName: user.profile?.nickname || user.profile?.username
    })
    showToast('已驳回')
    showReject.value = false
    router.replace('/audit')
  } catch (e: any) {
    showToast(e?.message || '操作失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <van-nav-bar title="审核详情" left-arrow @click-left="router.back()" fixed placeholder />
    <template v-if="detail">
      <van-cell-group inset title="工单信息">
        <van-cell title="工单号" :value="detail.orderNo" />
        <van-cell title="区域" :value="detail.area" />
        <van-cell title="优先级" :value="detail.priorityCode" />
        <van-cell title="调度任务" :value="detail.dispatchTaskId" />
        <van-cell title="分配终端" :value="detail.terminalName" />
        <van-cell title="审核截止" :value="detail.auditDeadline" />
      </van-cell-group>
      <van-cell-group v-if="detail.assignReason" inset title="分配理由">
        <van-cell :label="detail.assignReason" />
      </van-cell-group>
      <van-cell-group inset title="巡检步骤">
        <van-cell
          v-for="s in detail.steps || []"
          :key="s.stepOrder"
          :title="`#${s.stepOrder} ${s.type}`"
          :label="[s.deviceName, s.checkItem, s.standardRange, s.description].filter(Boolean).join(' · ')"
        />
      </van-cell-group>
      <div class="actions">
        <van-button type="primary" block :loading="busy" @click="onApprove">审核通过</van-button>
        <van-button type="danger" plain block @click="showReject = true">驳回</van-button>
      </div>
    </template>
    <van-popup v-model:show="showReject" position="bottom" round>
      <div class="reject-box">
        <div class="reject-title">驳回原因</div>
        <van-field v-model="rejectReason" type="textarea" rows="3" placeholder="请填写驳回原因" />
        <van-button type="danger" block :loading="busy" @click="onReject">确认驳回</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: #f5f6f8;
  padding-bottom: 24px;
}
.actions {
  margin: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.reject-box {
  padding: 16px;
}
.reject-title {
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
