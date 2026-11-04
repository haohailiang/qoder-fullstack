<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createUser, deleteUser, listUsers, updateUser, type User } from '@/api/user'

const loading = ref(false)
const keyword = ref('')
const users = ref<User[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  email: '',
  age: 18,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] },
  ],
  age: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
}

const filteredUsers = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return users.value
  return users.value.filter((u) => u.name.includes(kw) || u.email.includes(kw))
})

async function loadUsers() {
  loading.value = true
  try {
    users.value = await listUsers()
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.email = ''
  form.age = 18
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row: User) {
  editingId.value = row.id ?? null
  form.name = row.name
  form.email = row.email
  form.age = row.age
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value == null) {
      await createUser({ ...form })
      ElMessage.success('新增成功')
    } else {
      await updateUser(editingId.value, { ...form })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: User) {
  if (row.id == null) return
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return // 用户取消
  }
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  await loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div class="user-manage">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
          <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
        </div>
        <el-input
          v-model="keyword"
          placeholder="按用户名 / 邮箱搜索"
          :prefix-icon="Search"
          clearable
          style="width: 260px"
        />
      </div>

      <el-table v-loading="loading" :data="filteredUsers" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="账号" min-width="120">
          <template #default="{ row }">
            <!-- 注册产生的账号才有 username，旧 CRUD 数据为空时显示占位符 -->
            <span v-if="row.username">{{ row.username }}</span>
            <el-text v-else type="info">-</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="age" label="年龄" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增用户' : '编辑用户'"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="150" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-manage {
  max-width: 960px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
