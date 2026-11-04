import request from './request'

/**
 * 短信发送记录（与后端 dto.SmsRecord 对应）。
 * 演示项目直接返回明文验证码，仅供已登录用户在用户管理页查看。
 */
export interface SmsRecord {
  /** 接收手机号 */
  phone: string
  /** 本次发送的 6 位数字验证码（明文，演示用途） */
  code: string
  /** 发送时间（后端 ISO 字符串，LocalDateTime 序列化） */
  sentAt: string
  /** 发送状态：SUCCESS / FAILED */
  status: string
}

/**
 * 查询短信验证码发送记录（需登录访问）：返回全部记录，按发送时间倒序（最新在前）。
 * 短信发送动作本身已迁移到登录 / 注册页（api/auth.ts 的 sendSmsCode），此处仅做记录查看。
 */
export function getSmsRecords() {
  return request.get<SmsRecord[]>('/sms/records')
}
