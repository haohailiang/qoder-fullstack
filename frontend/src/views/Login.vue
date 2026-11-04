<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { setAuth } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入认证标识（用户名）', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const result = await login({ ...form })
    // 登录成功：保存 JWT 与用户信息（localStorage，刷新/重开标签页仍保持登录态）
    setAuth(result.token, result.user)
    ElMessage.success('登录成功')
    // 支持路由守卫记录的 redirect 参数，默认跳用户管理页
    const redirect = route.query.redirect as string | undefined
    router.push(redirect || '/users')
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card shadow="always" class="auth-card">
      <template #header>
        <div class="auth-header">
          <span class="auth-title">用户登录</span>
          <el-link type="primary" :underline="false" @click="router.push('/register')">
            还没有账号？去注册
          </el-link>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="认证标识（用户名）"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-tip">演示账号：zhangsan / 123456（或 lisi / 123456）</div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.auth-card {
  width: 400px;
}

.auth-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.auth-title {
  font-size: 18px;
  font-weight: 600;
}

.auth-submit {
  width: 100%;
}

.auth-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
