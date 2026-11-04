import request from './request'

/**
 * 向后端短信发送接口发起请求：由后端根据 userId 查询手机号并通过 SMS 服务
 * 调用云片网发送验证码（前端不直接接触云片网 API / apiKey 等敏感配置）。
 * 后端会做同一手机号 60 秒内的发送频率限制，前端也应配合倒计时禁用按钮，避免重复点击。
 */
export function sendSmsCode(userId: number) {
  return request.post<void>('/sms/send-code', { userId })
}
