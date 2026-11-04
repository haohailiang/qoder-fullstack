package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体
 */
@Data
public class LoginRequest {

    @NotBlank(message = "认证标识（用户名）不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
