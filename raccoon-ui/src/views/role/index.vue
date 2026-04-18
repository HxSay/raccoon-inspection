<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoleList, addRole, updateRole, RoleResponse, RoleRequest } from '@/api/role'

const loading = ref(false)
const roleList = ref<RoleResponse[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const roleForm = ref<RoleRequest>({
  id: undefined,
  roleName: '',
  roleCode: '',
  description: '',
  sort: 0,
  status: 1
})

const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getRoleList()
    roleList.value = res.data
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  dialogTitle.value = '新增角色'
  roleForm.value = {
    id: undefined,
    roleName: '',
    roleCode: '',
    description: '',
    sort: 0,
    status: 1
  }
  dialogVisible.value = true
}

const openEditDialog = (row: RoleResponse) => {
  dialogTitle.value = '编辑角色'
  roleForm.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (roleForm.value.id) {
      await updateRole(roleForm.value)
      ElMessage.success('编辑成功')
    } else {
      await addRole(roleForm.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="role-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>角色列表</span>
          <el-button type="primary" @click="openAddDialog">新增角色</el-button>
        </div>
      </template>
      <el-table :data="roleList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="roleCode" label="角色编码" />
        <el-table-column prop="description" label="角色描述" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="roleForm" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" :disabled="!!roleForm.id" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="roleForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="roleForm.status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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