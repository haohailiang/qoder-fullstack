package com.ecommerce.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信发送记录：每次成功 / 失败发送短信验证码时记录一条，保存在进程内内存（不落库，重启即失效）。
 * 演示项目直接以明文返回验证码内容，便于用户管理页联调查看；仅登录用户可通过 /sms/records 查询。
 */
@Data
public class SmsRecord {

    /** 接收手机号 */
    private String phone;

    /** 本次发送的 6 位数字验证码（演示用途明文展示） */
    private String code;

    /** 发送时间（服务端时间） */
    private LocalDateTime sentAt;

    /** 发送状态：SUCCESS（成功）/ FAILED（失败） */
    private String status;

    public SmsRecord(String phone, String code, LocalDateTime sentAt, String status) {
        this.phone = phone;
        this.code = code;
        this.sentAt = sentAt;
        this.status = status;
    }
}
