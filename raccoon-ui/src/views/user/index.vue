<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, addUser, updateUser, updateUserStatus, resetUserPassword, deleteUser, UserResponse, UserQueryRequest } from '@/api/user'

const loading = ref(false)
const userList = ref<UserResponse[]>([])
const total = ref(0)
const queryParams = ref<UserQueryRequest>({
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const userForm = ref({
  id: undefined as number | undefined,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  gender: 0,
  userType: 2,
  status: 1
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
]

const genderOptions = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 }
]

const userTypeOptions = [
  { label: 'C端用户', value: 1 },
  { label: 'B端管理员', value: 2 }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getUserPage(queryParams.value)
    userList.value = res.data.records
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
  dialogTitle.value = '新增用户'
  userForm.value = {
    id: undefined,
    username: '',
    nickname: '',
    email: '',
    phone: '',
    password: '',
    gender: 0,
    userType: 2,
    status: 1
  }
  dialogVisible.value = true
}

const openEditDialog = (row: UserResponse) => {
  dialogTitle.value = '编辑用户'
  userForm.value = { ...row, password: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (userForm.value.id) {
      await updateUser(userForm.value)
      ElMessage.success('编辑成功')
    } else {
      await addUser(userForm.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch {
    // error handled by interceptor
  }
}

const handleStatusChange = async (id: number, status: number) => {
  try {
    await updateUserStatus(id, status)
    ElMessage.success('状态修改成功')
    fetchData()
  } catch {
    // error handled by interceptor
  }
}

const handleResetPassword = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要重置密码为123456吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await resetUserPassword(id)
    ElMessage.success('密码重置成功')
  } catch {
    // user cancel
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(id)
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
  <div class="user-container">
    <el-card shadow="hover">
      <template #header>
        <span>用户查询</span>
      </template>
      <el-form :inline="true" :model="queryParams" class="query-form">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
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
          <span>用户列表</span>
          <el-button type="primary" @click="openAddDialog">新增用户</el-button>
        </div>
      </template>
      <el-table :data="userList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="gender" label="性别">
          <template #default="{ row }">
            {{ genderOptions.find(g => g.value === row.gender)?.label }}
          </template>
        </el-table-column>
        <el-table-column prop="userType" label="用户类型">
          <template #default="{ row }">
            {{ userTypeOptions.find(t => t.value === row.userType)?.label }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="handleStatusChange(row.id, row.status === 1 ? 0 : 1)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="warning" @click="handleResetPassword(row.id)">重置密码</el-button>
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
      <el-form :model="userForm" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="!!userForm.id" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="密码" v-if="!userForm.id">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="userForm.gender">
            <el-option v-for="item in genderOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="userForm.userType">
            <el-option v-for="item in userTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
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