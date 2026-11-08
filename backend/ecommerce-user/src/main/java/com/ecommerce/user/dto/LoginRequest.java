package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体
 * <p>
 * 注意：captchaKey / captchaValue / smsCode 在 admin 用户登录时可为空，
 * 具体校验逻辑在 {@link com.ecommerce.user.service.impl.AuthServiceImpl#login} 中按用户名区分。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "认证标识（用户名）不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图片验证码 key（admin 登录时可为空） */
    private String captchaKey;

    /** 图片验证码值（admin 登录时可为空） */
    private String captchaValue;

    /** 短信验证码（admin 登录时可为空） */
    private String smsCode;
}
