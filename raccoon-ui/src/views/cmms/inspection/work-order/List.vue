<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { iwoPage, iwoMyPage, iwoCancel, type IwoRow } from '@/api/inspectionWorkOrder'

const router = useRouter()

const mode = ref<'all' | 'my'>('all')
const myTab = ref<'pending' | 'running' | 'done'>('pending')

const query = ref({
  page: 1,
  size: 10,
  area: '',
  shiftType: undefined as number | undefined,
  status: undefined as number | undefined,
  planStartFrom: '',
  planStartTo: ''
})

const list = ref<IwoRow[]>([])
const total = ref(0)

const shiftOpt = [
  { label: '早班', value: 1 },
  { label: '中班', value: 2 },
  { label: '夜班', value: 3 }
]

const statusOpt = [
  { label: '待下发', value: 1 },
  { label: '待执行', value: 2 },
  { label: '执行中', value: 3 },
  { label: '已完成', value: 4 },
  { label: '已取消', value: 5 }
]

const statusLabel = (s: number) => statusOpt.find((x) => x.value === s)?.label ?? String(s)

const load = async () => {
  if (mode.value === 'all') {
    const res: any = await iwoPage(query.value)
    list.value = res.data.records
    total.value = res.data.total
  } else {
    const res: any = await iwoMyPage({
      page: query.value.page,
      size: query.value.size,
      tab: myTab.value
    })
    list.value = res.data.records
    total.value = res.data.total
  }
}

watch([mode, myTab], () => {
  query.value.page = 1
  load()
})

onMounted(load)

const cancel = async (row: IwoRow) => {
  await ElMessageBox.confirm('确认取消该工单？', '取消')
  await iwoCancel(row.id)
  ElMessage.success('已取消')
  load()
}
</script>

<template>
  <div class="page">
    <el-radio-group v-model="mode" style="margin-bottom: 12px">
      <el-radio-button label="all">全部工单</el-radio-button>
      <el-radio-button label="my">我的工单</el-radio-button>
    </el-radio-group>

    <el-alert
      v-if="mode === 'all'"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    >
      「待下发」工单不在此页下发；请切换到「巡检任务」页，对关联任务点击「下发」，将工单下发至任务上的执行人（巡检人）。
    </el-alert>

    <template v-if="mode === 'my'">
      <el-alert type="success" :closable="false" show-icon style="margin-bottom: 12px">
        手机端执行人可打开
        <router-link to="/m/patrol">我的巡检工单（/m/patrol）</router-link>
        ，查看待执行工单并逐步填报。
      </el-alert>
      <el-radio-group v-model="myTab" size="small" style="margin-bottom: 12px">
        <el-radio-button label="pending">待执行</el-radio-button>
        <el-radio-button label="running">执行中</el-radio-button>
        <el-radio-button label="done">已完成</el-radio-button>
      </el-radio-group>
    </template>

    <el-form v-if="mode === 'all'" :inline="true" class="filter-form">
      <el-form-item label="区域">
        <el-input v-model="query.area" clearable placeholder="模糊匹配" style="width: 160px" />
      </el-form-item>
      <el-form-item label="班次">
        <el-select v-model="query.shiftType" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in shiftOpt" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in statusOpt" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划开始起">
        <el-date-picker
          v-model="query.planStartFrom"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="起"
          style="width: 190px"
        />
      </el-form-item>
      <el-form-item label="止">
        <el-date-picker
          v-model="query.planStartTo"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="止"
          style="width: 190px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="() => { query.page = 1; load() }">查询</el-button>
        <el-button type="success" @click="router.push('/cmms/inspection/work-order/form')">新建工单</el-button>
      </el-form-item>
    </el-form>

    <div v-else style="margin-bottom: 12px">
      <el-button type="success" @click="router.push('/cmms/inspection/work-order/form')">新建工单</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="orderNo" label="工单号" min-width="150" />
      <el-table-column prop="planId" label="计划ID" width="90" />
      <el-table-column prop="taskId" label="任务ID" width="90" />
      <el-table-column prop="area" label="区域" width="120" />
      <el-table-column label="班次" width="90">
        <template #default="{ row }">{{ shiftOpt.find((s) => s.value === row.shiftType)?.label }}</template>
      </el-table-column>
      <el-table-column prop="inspectorName" label="巡检人" width="100" />
      <el-table-column prop="planStartTime" label="计划开始" min-width="160" />
      <el-table-column prop="planEndTime" label="计划结束" min-width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/cmms/inspection/work-order/detail/${row.id}`)">详情</el-button>
          <el-button
            v-if="row.status === 2 || row.status === 3"
            link
            type="warning"
            @click="router.push(`/cmms/inspection/work-order/execute/${row.id}`)"
            >执行</el-button
          >
          <el-button v-if="row.status === 1 || row.status === 2" link type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="load"
    />
  </div>
</template>

<style scoped>
.page {
  padding: 8px;
}
.filter-form {
  flex-wrap: wrap;
}
</style>
