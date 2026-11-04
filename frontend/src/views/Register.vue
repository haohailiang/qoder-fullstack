<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCaptcha, register } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 字段与后端 dto.RegisterRequest 一一对应
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  name: '',
  email: '',
  phone: '',
  age: 18,
  captchaValue: '',
})

// 验证码 key 由后端生成并随图片一起返回，提交时原样回传给后端定位本次验证码
const captchaKey = ref('')
const captchaImage = ref('')

function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('确认密码与密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入认证标识（用户名）', trigger: 'blur' },
    { min: 3, max: 32, message: '认证标识长度需在 3-32 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: ['blur', 'change'] },
  ],
  age: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  captchaValue: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha() {
  try {
    const result = await getCaptcha()
    captchaKey.value = result.captchaKey
    captchaImage.value = result.captchaImage
    form.captchaValue = ''
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  }
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({ ...form, captchaKey: captchaKey.value })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理（如“认证标识已存在”“验证码错误或已过期”）
    // 验证码为一次性使用，失败后自动刷新，允许用户重新输入
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="auth-page">
    <el-card shadow="always" class="auth-card">
      <template #header>
        <div class="auth-header">
          <span class="auth-title">用户注册</span>
          <el-link type="primary" :underline="false" @click="router.push('/login')">
            已有账号？去登录
          </el-link>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="认证标识" prop="username">
          <el-input v-model="form.username" placeholder="登录用的用户名，3-32 个字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="用于接收短信验证码" />
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="150" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaValue">
          <div class="captcha-row">
            <el-input v-model="form.captchaValue" placeholder="请输入验证码" />
            <el-image
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-image"
              alt="点击刷新验证码"
              fit="fill"
              @click="refreshCaptcha"
            />
            <el-link v-else type="primary" :underline="false" @click="refreshCaptcha">
              获取验证码
            </el-link>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="handleRegister">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-tip">点击图片可刷新验证码</div>
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
  width: 460px;
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

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.captcha-image {
  width: 130px;
  height: 44px;
  flex-shrink: 0;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.auth-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
