<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getCaptcha, register, sendSmsCode } from '@/api/auth'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/** 与后端 sms.rate-limit-seconds 保持一致的倒计时秒数，防止重复发送 */
const SMS_COUNTDOWN_SECONDS = 60

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)
// 验证码图片加载（小区域，立即展示，不叠加页面级 loading）
const captchaLoading = ref(false)

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
  smsCode: '',
})

// 验证码 key 由后端生成并随图片一起返回，提交时原样回传给后端定位本次验证码
const captchaKey = ref('')
const captchaImage = ref('')

// 短信发送倒计时（秒），> 0 时禁用发送按钮
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

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

function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback(new Error(t('register.confirmPasswordRequired')))
  } else if (value !== form.password) {
    callback(new Error(t('register.confirmPasswordMismatch')))
  } else {
    callback()
  }
}

// 使用 computed 使校验文案随语言切换实时更新（rules 变化不会触发校验，见 el-form 的 validate-on-rule-change）
const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: t('register.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 32, message: t('register.usernameLength'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('register.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 64, message: t('register.passwordLength'), trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
  name: [{ required: true, message: t('register.nameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: t('register.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('register.emailInvalid'), trigger: ['blur', 'change'] },
  ],
  phone: [
    { required: true, message: t('register.phoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('register.phoneInvalid'), trigger: ['blur', 'change'] },
  ],
  age: [{ required: true, message: t('register.ageRequired'), trigger: 'blur' }],
  captchaValue: [{ required: true, message: t('register.captchaRequired'), trigger: 'blur' }],
  smsCode: [{ required: true, message: t('register.smsCodeRequired'), trigger: 'blur' }],
}))

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const result = await getCaptcha()
    captchaKey.value = result.captchaKey
    captchaImage.value = result.captchaImage
    form.captchaValue = ''
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    captchaLoading.value = false
  }
}

/**
 * 发送短信验证码：先携带图片验证码 key/value + 手机号提交，后端校验图片验证码通过后才真正发送。
 * 发送成功后进入倒计时；后端对图片验证码只校验不消耗，因此保留已填写的验证码与图片，
 * 用户无需重新输入即可直接提交注册。
 */
async function handleSendSmsCode() {
  if (countdown.value > 0 || sending.value) return
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning(t('register.enterCorrectPhone'))
    return
  }
  if (!form.captchaValue) {
    ElMessage.warning(t('register.enterCaptcha'))
    return
  }
  sending.value = true
  try {
    await sendSmsCode({
      phone: form.phone,
      captchaKey: captchaKey.value,
      captchaValue: form.captchaValue,
    })
    ElMessage.success(t('register.smsSent'))
    startCountdown()
  } catch {
    // 图片验证码错误 / 频控等提示已由拦截器统一处理；刷新验证码便于重新输入
    await refreshCaptcha()
  } finally {
    sending.value = false
  }
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({ ...form, captchaKey: captchaKey.value })
    ElMessage.success(t('register.registerSuccess'))
    router.push('/login')
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理（如"认证标识已存在""验证码错误或已过期"）
    // 验证码为一次性使用，失败后自动刷新，允许用户重新输入
    await refreshCaptcha()
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
          <span class="auth-title">{{ t('register.title') }}</span>
          <el-link type="primary" :underline="false" @click="router.push('/login')">
            {{ t('register.goLogin') }}
          </el-link>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" :validate-on-rule-change="false" label-width="90px">
        <el-form-item :label="t('register.username')" prop="username">
          <el-input v-model="form.username" :placeholder="t('register.usernamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('register.password')" prop="password">
          <el-input v-model="form.password" type="password" :placeholder="t('register.passwordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('register.confirmPassword')" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('register.confirmPasswordPlaceholder')"
            show-password
          />
        </el-form-item>
        <el-form-item :label="t('register.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('register.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('register.email')" prop="email">
          <el-input v-model="form.email" :placeholder="t('register.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('register.phone')" prop="phone">
          <el-input v-model="form.phone" :placeholder="t('register.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('register.age')" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="150" />
        </el-form-item>
        <el-form-item :label="t('register.captcha')" prop="captchaValue">
          <div class="captcha-row">
            <el-input v-model="form.captchaValue" :placeholder="t('register.captchaPlaceholder')" />
            <div
              class="captcha-image-wrapper"
              v-loading="captchaLoading"
              element-loading-background="transparent"
              element-loading-size="16"
            >
              <el-image
                v-if="captchaImage"
                :src="captchaImage"
                class="captcha-image"
                :alt="t('login.captchaAlt')"
                fit="fill"
                @click="refreshCaptcha"
              />
              <el-link v-else type="primary" :underline="false" @click="refreshCaptcha">
                {{ t('login.getCaptcha') }}
              </el-link>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('register.smsCode')" prop="smsCode">
          <div class="sms-row">
            <el-input v-model="form.smsCode" :placeholder="t('register.smsCodePlaceholder')" />
            <el-button
              class="sms-send-btn"
              :disabled="countdown > 0"
              :loading="sending"
              @click="handleSendSmsCode"
            >
              {{ countdown > 0 ? t('register.resend', { seconds: countdown }) : t('register.sendSms') }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="handleRegister">
            {{ t('register.submit') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-tip">{{ t('register.tipSms') }}</div>
      <div class="auth-tip">{{ t('register.tipRefresh') }}</div>
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
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
}

.captcha-image-wrapper {
  position: relative;
  width: 130px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sms-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.sms-send-btn {
  flex-shrink: 0;
  width: 120px;
}

.auth-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  text-align: center;
}
</style>
