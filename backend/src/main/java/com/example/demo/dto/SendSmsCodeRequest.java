package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送短信验证码请求体：前端用户管理页只需传用户 id，
 * 手机号由后端根据 id 查询用户记录获得（避免前端直接传入未校验的号码）。
 */
@Data
public class SendSmsCodeRequest {

    @NotNull(message = "用户 id 不能为空")
    private Long userId;
}
