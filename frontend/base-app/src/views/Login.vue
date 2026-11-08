<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Message, Picture, User } from '@element-plus/icons-vue'
import { getCaptcha, login, sendSmsCode } from '@/api/auth'
import { setAuth } from '@/utils/auth'
import { setTheme, type ThemeMode } from '@/utils/theme'

/** 与后端 sms.rate-limit-seconds 保持一致的倒计时秒数，防止重复发送 */
const SMS_COUNTDOWN_SECONDS = 60

/** 管理员用户名：admin 登录时跳过图片验证码和短信验证码 */
const ADMIN_USERNAME = 'admin'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)

const form = reactive({
  username: '',
  password: '',
  captchaValue: '',
  smsCode: '',
})

// 验证码 key 由后端生成并随图片一起返回，提交时原样回传给后端定位本次验证码
const captchaKey = ref('')
const captchaImage = ref('')

// 短信发送倒计时（秒），> 0 时禁用发送按钮
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 判断当前输入的用户名是否为 admin（忽略大小写） */
const isAdmin = computed(() => form.username.trim().toLowerCase() === ADMIN_USERNAME)

function startCountdown() {
  countdown.value = SMS_COUNTDOWN_SECONDS
  countdownTimer = setInterval(() => {
    if (countdown.value > 1) {
      countdown.value -= 1
    } else {
      countdown.value = 0
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

/** 表单校验规则：admin 用户无需验证码和短信，动态移除对应必填校验 */
const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: '请输入认证标识（用户名）', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
  ...(isAdmin.value
    ? {}
    : {
        captchaValue: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
        smsCode: [{ required: true, message: '请输入短信验证码', trigger: 'blur' }],
      }),
}))

// 切换到 admin 时自动刷新验证码（避免过期），切回普通用户时也刷新
watch(isAdmin, () => {
  if (!isAdmin.value) {
    refreshCaptcha()
  }
})

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

/**
 * 发送短信验证码（两步验证第一步之后的动作）：
 * 先携带图片验证码 key/value + 用户名提交，后端校验图片验证码通过后才真正发送。
 * 发送成功后进入倒计时，并刷新图片验证码（一次性）。
 */
async function handleSendSmsCode() {
  if (countdown.value > 0 || sending.value) return
  if (!form.username) {
    ElMessage.warning('请先输入认证标识（用户名）')
    return
  }
  if (!form.captchaValue) {
    ElMessage.warning('请先输入图片验证码')
    return
  }
  sending.value = true
  try {
    await sendSmsCode({
      username: form.username,
      captchaKey: captchaKey.value,
      captchaValue: form.captchaValue,
    })
    ElMessage.success('短信验证码已发送')
    startCountdown()
    // 图片验证码一次性使用，发送成功后自动刷新
    await refreshCaptcha()
  } catch {
    // 图片验证码错误 / 频控等提示已由拦截器统一处理；刷新验证码便于重新输入
    await refreshCaptcha()
  } finally {
    sending.value = false
  }
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // admin 用户登录无需验证码，传空字符串即可（后端会跳过校验）
    const loginData = isAdmin.value
      ? { username: form.username, password: form.password, captchaKey: '', captchaValue: '', smsCode: '' }
      : { ...form, captchaKey: captchaKey.value }
    const result = await login(loginData)
    // 登录成功：保存 JWT 与用户信息（localStorage，刷新/重开标签页仍保持登录态）
    setAuth(result.token, result.user)
    // 登录成功后从用户数据中读取主题偏好并应用（数据库持久化）
    if (result.user.themePreference) {
      await setTheme(result.user.themePreference as ThemeMode)
    }
    ElMessage.success('登录成功')
    // 支持路由守卫记录的 redirect 参数，默认跳用户管理页
    const redirect = route.query.redirect as string | undefined
    router.push(redirect || '/users')
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理（如"验证码错误或已过期"）
    // 验证码为一次性使用，失败后自动刷新，允许用户重新输入（admin 无需刷新）
    if (!isAdmin.value) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
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
          />
        </el-form-item>
        <el-form-item prop="captchaValue" v-if="!isAdmin">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaValue"
              placeholder="验证码"
              :prefix-icon="Picture"
              @keyup.enter="handleLogin"
            />
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
        <el-form-item prop="smsCode" v-if="!isAdmin">
          <div class="sms-row">
            <el-input
              v-model="form.smsCode"
              placeholder="短信验证码"
              :prefix-icon="Message"
              @keyup.enter="handleLogin"
            />
            <el-button
              class="sms-send-btn"
              :disabled="countdown > 0"
              :loading="sending"
              @click="handleSendSmsCode"
            >
              {{ countdown > 0 ? `重新发送(${countdown}s)` : '发送短信验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-tip">管理员账号：admin / 123456（无需验证码，直接登录）</div>
      <div class="auth-tip">演示账号：zhangsan / 123456（或 lisi / 123456）</div>
      <div class="auth-tip" v-if="!isAdmin">先输入图片验证码点「发送短信验证码」（演示短信默认关闭，验证码可在用户管理页查看）</div>
      <div class="auth-tip" v-if="!isAdmin">点击图片可刷新验证码</div>
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
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
}

.sms-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.sms-send-btn {
  flex-shrink: 0;
  width: 130px;
}

.auth-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  text-align: center;
}
</style>
