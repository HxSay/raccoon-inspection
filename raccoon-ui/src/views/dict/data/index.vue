<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictDataPage, addDictData, updateDictData, deleteDictData, DictDataResponse, DictDataQueryRequest, DictDataRequest } from '@/api/dictData'
import { getDictTypeList, DictTypeResponse } from '@/api/dict'

const loading = ref(false)
const dictDataList = ref<DictDataResponse[]>([])
const total = ref(0)
const queryParams = ref<DictDataQueryRequest>({
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增字典数据')
const dictDataForm = ref<DictDataRequest>({
  dictTypeCode: '',
  dictLabel: '',
  dictValue: '',
  dictExtend: '',
  status: 1,
  sort: 0,
  remark: ''
})

const dictTypeOptions = ref<DictTypeResponse[]>([])

const rules = {
  dictTypeCode: [{ required: true, message: '请选择字典类型', trigger: 'change' }],
  dictLabel: [{ required: true, message: '请输入字典显示名称', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典存储值', trigger: 'blur' }]
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getDictDataPage(queryParams.value)
    dictDataList.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchDictTypeOptions = async () => {
  try {
    const res = await getDictTypeList()
    dictTypeOptions.value = res.data
  } catch (error) {
    console.error('获取字典类型列表失败', error)
  }
}

const handleQuery = () => {
  queryParams.value.page = 1
  fetchData()
}

const handleReset = () => {
  queryParams.value = {
    page: 1,
    size: 10
  }
  fetchData()
}

const handlePageChange = (page: number) => {
  queryParams.value.page = page
  fetchData()
}

const handleSizeChange = (size: number) => {
  queryParams.value.size = size
  fetchData()
}

const openAddDialog = () => {
  dialogTitle.value = '新增字典数据'
  dictDataForm.value = {
    dictTypeCode: '',
    dictLabel: '',
    dictValue: '',
    dictExtend: '',
    status: 1,
    sort: 0,
    remark: ''
  }
  dialogVisible.value = true
}

const openEditDialog = (row: DictDataResponse) => {
  dialogTitle.value = '编辑字典数据'
  dictDataForm.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (dictDataForm.value.id) {
      await updateDictData(dictDataForm.value)
      ElMessage.success('编辑成功')
    } else {
      await addDictData(dictDataForm.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch {
    // error handled by interceptor
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该字典数据吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteDictData(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // user cancel
  }
}

const handleExtendChange = (val?: string | number) => {
  if (!val) return
  try {
    JSON.parse(String(val))
  } catch {
    ElMessage.warning('JSON格式不正确')
  }
}

onMounted(() => {
  fetchData()
  fetchDictTypeOptions()
})
</script>

<template>
  <div class="dict-data-container">
    <el-card shadow="hover">
      <template #header>
        <span>字典数据查询</span>
      </template>
      <el-form :inline="true" :model="queryParams" class="query-form">
        <el-form-item label="字典类型">
          <el-select v-model="queryParams.dictTypeCode" placeholder="请选择字典类型" clearable filterable>
            <el-option v-for="item in dictTypeOptions" :key="item.dictTypeCode" :label="item.dictTypeName" :value="item.dictTypeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="字典显示名称">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入字典显示名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>字典数据列表</span>
          <el-button type="primary" @click="openAddDialog">新增字典数据</el-button>
        </div>
      </template>
      <el-table :data="dictDataList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictTypeCode" label="字典类型编码" />
        <el-table-column prop="dictLabel" label="显示名称" />
        <el-table-column prop="dictValue" label="存储值" />
        <el-table-column prop="dictExtend" label="扩展字段" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.dictExtend || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="dictDataForm" :rules="rules" label-width="120px">
        <el-form-item label="字典类型" prop="dictTypeCode">
          <el-select v-model="dictDataForm.dictTypeCode" placeholder="请选择字典类型" filterable :disabled="!!dictDataForm.id" style="width: 100%">
            <el-option v-for="item in dictTypeOptions" :key="item.dictTypeCode" :label="item.dictTypeName" :value="item.dictTypeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称" prop="dictLabel">
          <el-input v-model="dictDataForm.dictLabel" placeholder="请输入字典显示名称" />
        </el-form-item>
        <el-form-item label="存储值" prop="dictValue">
          <el-input v-model="dictDataForm.dictValue" placeholder="请输入字典存储值" />
        </el-form-item>
        <el-form-item label="扩展字段">
          <el-input v-model="dictDataForm.dictExtend" type="textarea" :rows="3" placeholder='请输入JSON格式的扩展字段，如：{"color":"#1890FF"}' @blur="handleExtendChange(dictDataForm.dictExtend)" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dictDataForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="dictDataForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dictDataForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
