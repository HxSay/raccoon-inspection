<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictTypePage, addDictType, updateDictType, deleteDictType, DictTypeResponse, DictTypeQueryRequest, DictTypeRequest } from '@/api/dict'

const loading = ref(false)
const dictTypeList = ref<DictTypeResponse[]>([])
const total = ref(0)
const queryParams = ref<DictTypeQueryRequest>({
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增字典类型')
const dictTypeForm = ref<DictTypeRequest>({
  dictTypeCode: '',
  dictTypeName: '',
  status: 1,
  remark: '',
  sort: 0
})

const rules = {
  dictTypeCode: [{ required: true, message: '请输入字典类型编码', trigger: 'blur' }],
  dictTypeName: [{ required: true, message: '请输入字典类型名称', trigger: 'blur' }]
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getDictTypePage(queryParams.value)
    dictTypeList.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
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
  dialogTitle.value = '新增字典类型'
  dictTypeForm.value = {
    dictTypeCode: '',
    dictTypeName: '',
    status: 1,
    remark: '',
    sort: 0
  }
  dialogVisible.value = true
}

const openEditDialog = (row: DictTypeResponse) => {
  dialogTitle.value = '编辑字典类型'
  dictTypeForm.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (dictTypeForm.value.id) {
      await updateDictType(dictTypeForm.value)
      ElMessage.success('编辑成功')
    } else {
      await addDictType(dictTypeForm.value)
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
    await ElMessageBox.confirm('确定要删除该字典类型吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteDictType(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // user cancel
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="dict-type-container">
    <el-card shadow="hover">
      <template #header>
        <span>字典类型查询</span>
      </template>
      <el-form :inline="true" :model="queryParams" class="query-form">
        <el-form-item label="字典类型编码">
          <el-input v-model="queryParams.dictTypeCode" placeholder="请输入字典类型编码" clearable />
        </el-form-item>
        <el-form-item label="字典类型名称">
          <el-input v-model="queryParams.dictTypeName" placeholder="请输入字典类型名称" clearable />
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
          <span>字典类型列表</span>
          <el-button type="primary" @click="openAddDialog">新增字典类型</el-button>
        </div>
      </template>
      <el-table :data="dictTypeList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictTypeCode" label="字典类型编码" />
        <el-table-column prop="dictTypeName" label="字典类型名称" />
        <el-table-column prop="status" label="状态">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="dictTypeForm" :rules="rules" label-width="120px">
        <el-form-item label="字典类型编码" prop="dictTypeCode">
          <el-input v-model="dictTypeForm.dictTypeCode" :disabled="!!dictTypeForm.id" placeholder="请输入字典类型编码" />
        </el-form-item>
        <el-form-item label="字典类型名称" prop="dictTypeName">
          <el-input v-model="dictTypeForm.dictTypeName" placeholder="请输入字典类型名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dictTypeForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="dictTypeForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dictTypeForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
