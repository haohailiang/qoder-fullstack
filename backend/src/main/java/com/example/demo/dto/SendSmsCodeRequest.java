package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录 / 注册页「发送短信验证码」请求体（POST /auth/send-sms-code，匿名可访问）。
 * <p>
 * 防滥发依赖图片验证码：先由后端校验 captchaKey / captchaValue，通过后才真正发送短信。
 * 手机号来源分两种场景：
 * - 注册场景：用户尚不存在，前端直接传入 {@code phone}；
 * - 登录场景：前端传入已注册的 {@code username}，由后端查询对应用户手机号。
 * phone 与 username 二者至少填写其一（具体校验在 AuthService 中完成）。
 */
@Data
public class SendSmsCodeRequest {

    @NotBlank(message = "验证码不能为空")
    private String captchaKey;

    @NotBlank(message = "请输入验证码")
    private String captchaValue;

    /** 注册场景：前端直接传入的目标手机号 */
    private String phone;

    /** 登录场景：已注册用户的认证标识，由后端查询其手机号 */
    private String username;
}
