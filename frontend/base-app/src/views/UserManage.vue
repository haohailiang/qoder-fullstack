<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createUser, deleteUser, listUsers, updateUser, type User } from '@/api/user'
import { getSmsRecords, type SmsRecord } from '@/api/sms'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 立即态：驱动刷新按钮自身的 loading（按钮不需要防闪烁）
const refreshing = ref(false)
const recordsRefreshing = ref(false)

// 区域态：延迟展示，避免快请求导致表格遮罩闪烁
const { visible: tableLoading, wrap: withTableLoading } = useDelayedLoading()
const { visible: recordsTableLoading, wrap: withRecordsTableLoading } = useDelayedLoading()

const keyword = ref('')
const users = ref<User[]>([])

const records = ref<SmsRecord[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const deletingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  email: '',
  phone: '',
  age: 18,
})

const rules: FormRules = {
  name: [{ required: true, message: t('users.nameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: t('users.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('users.emailInvalid'), trigger: ['blur', 'change'] },
  ],
  phone: [
    { required: true, message: t('users.phoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('users.phoneInvalid'), trigger: ['blur', 'change'] },
  ],
  age: [{ required: true, message: t('users.ageRequired'), trigger: 'blur' }],
}

const filteredUsers = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return users.value
  return users.value.filter((u) => u.name.includes(kw) || u.email.includes(kw))
})

// 首屏无数据时用骨架屏替代表格遮罩，同一区域不叠加两种 loading
const showTableSkeleton = computed(() => tableLoading.value && users.value.length === 0)
const showRecordsSkeleton = computed(() => recordsTableLoading.value && records.value.length === 0)

async function loadUsers() {
  refreshing.value = true
  try {
    users.value = await withTableLoading(() => listUsers())
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    refreshing.value = false
  }
}

function resetForm() {
  form.name = ''
  form.email = ''
  form.phone = ''
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
  form.phone = row.phone
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
      ElMessage.success(t('users.createSuccess'))
    } else {
      await updateUser(editingId.value, { ...form })
      ElMessage.success(t('users.updateSuccess'))
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
    await ElMessageBox.confirm(t('users.deleteConfirm', { name: row.name }), t('users.deleteConfirmTitle'), {
      type: 'warning',
      confirmButtonText: t('users.deleteConfirmBtn'),
      cancelButtonText: t('users.deleteCancelBtn'),
    })
  } catch {
    return // 用户取消
  }
  // 删除使用按钮自身 loading，不叠加区域遮罩
  deletingId.value = row.id
  try {
    await deleteUser(row.id)
    ElMessage.success(t('users.deleteSuccess'))
    await loadUsers()
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    deletingId.value = null
  }
}

/** 将后端 ISO 时间字符串（LocalDateTime 序列化）格式化为 YYYY-MM-DD HH:mm:ss */
function formatSentAt(sentAt: string) {
  if (!sentAt) return '-'
  return sentAt.replace('T', ' ').slice(0, 19)
}

async function loadRecords() {
  recordsRefreshing.value = true
  try {
    records.value = await withRecordsTableLoading(() => getSmsRecords())
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    recordsRefreshing.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadRecords()
})
</script>

<template>
  <div class="user-manage">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">{{ t('users.addUser') }}</el-button>
          <el-button :icon="Refresh" :loading="refreshing" @click="loadUsers">{{ t('users.refresh') }}</el-button>
        </div>
        <el-input
          v-model="keyword"
          :placeholder="t('users.searchPlaceholder')"
          :prefix-icon="Search"
          clearable
          style="width: 260px"
        />
      </div>

      <!-- 首屏加载用骨架屏，已有数据刷新时用表格遮罩，两者互斥 -->
      <el-skeleton v-if="showTableSkeleton" class="table-skeleton" :rows="5" animated />
      <el-table v-else v-loading="tableLoading" :data="filteredUsers" border stripe>
        <el-table-column prop="id" :label="t('users.colId')" width="80" />
        <el-table-column :label="t('users.colUsername')" min-width="120">
          <template #default="{ row }">
            <!-- 注册产生的账号才有 username，旧 CRUD 数据为空时显示占位符 -->
            <span v-if="row.username">{{ row.username }}</span>
            <el-text v-else type="info">-</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="t('users.colName')" min-width="100" />
        <el-table-column prop="email" :label="t('users.colEmail')" min-width="180" />
        <el-table-column prop="phone" :label="t('users.colPhone')" width="130" />
        <el-table-column prop="age" :label="t('users.colAge')" width="80" />
        <el-table-column :label="t('users.colActions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">{{ t('users.actionEdit') }}</el-button>
            <el-button link type="danger" :loading="deletingId === row.id" @click="handleDelete(row)">{{ t('users.actionDelete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="sms-records-card">
      <div class="toolbar">
        <span class="section-title">{{ t('users.smsRecords') }}</span>
        <el-button :icon="Refresh" :loading="recordsRefreshing" @click="loadRecords">{{ t('users.refresh') }}</el-button>
      </div>

      <el-skeleton v-if="showRecordsSkeleton" class="table-skeleton" :rows="3" animated />
      <el-table
        v-else
        v-loading="recordsTableLoading"
        :data="records"
        border
        stripe
        :empty-text="t('users.smsEmpty')"
      >
        <el-table-column prop="phone" :label="t('users.smsColPhone')" width="140" />
        <el-table-column prop="code" :label="t('users.smsColCode')" width="120" />
        <el-table-column :label="t('users.smsColStatus')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.status === 'SUCCESS' ? t('users.smsStatusSuccess') : t('users.smsStatusFailed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('users.smsColTime')" min-width="180">
          <template #default="{ row }">{{ formatSentAt(row.sentAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? t('users.addUser') : t('users.editUser')"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item :label="t('users.formName')" prop="name">
          <el-input v-model="form.name" :placeholder="t('users.formNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('users.formEmail')" prop="email">
          <el-input v-model="form.email" :placeholder="t('users.formEmailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('users.formPhone')" prop="phone">
          <el-input v-model="form.phone" :placeholder="t('users.formPhonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('users.formAge')" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="150" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('users.formCancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('users.formSubmit') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-manage {
  max-width: 1080px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sms-records-card {
  margin-top: 16px;
}

.table-skeleton {
  padding: 8px 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
}
</style>
