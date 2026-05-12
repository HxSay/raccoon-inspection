<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { deviceGet, pointList } from '@/api/cmms'

const route = useRoute()
const device = ref<Record<string, unknown> | null>(null)
const points = ref<Record<string, unknown>[]>([])
const loading = ref(true)

onMounted(async () => {
  const id = Number(route.params.id)
  const res: any = await deviceGet(id)
  device.value = res.data
  const pr: any = await pointList(id)
  points.value = pr.data || []
  loading.value = false
})

const statusMap: Record<number, string> = {
  1: '正常运行',
  2: '待维护',
  3: '故障停机',
  4: '闲置',
  5: '报废'
}
</script>

<template>
  <div>
    <van-nav-bar title="设备详情" left-arrow fixed placeholder @click-left="$router.back()" />
    <van-skeleton title avatar :row="4" :loading="loading">
      <template v-if="device">
        <van-cell-group inset>
          <van-cell title="设备名称" :value="String(device.deviceName)" />
          <van-cell title="设备编号" :value="String(device.deviceCode)" />
          <van-cell title="型号" :value="String(device.model || '-')" />
          <van-cell title="状态" :value="statusMap[Number(device.status)] || '-'" />
          <van-cell title="安装位置" :value="String(device.location || '-')" />
          <van-cell title="巡检周期(天)" :value="String(device.inspectionCycle ?? '-')" />
        </van-cell-group>
        <div class="sec-title">巡检点（inspection_point）</div>
        <van-cell-group inset>
          <van-cell v-for="p in points" :key="String(p.id)" :title="String(p.pointName)" :label="`类型 ${p.pointType} 单位 ${p.unit || '-'}`" />
          <van-empty v-if="!points.length" description="暂无巡检点" />
        </van-cell-group>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.sec-title {
  margin: 16px 16px 8px;
  font-size: 14px;
  color: #666;
}
</style>
