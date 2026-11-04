package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.Data;

/**
 * 登录成功响应：JWT + 用户基本信息 + 有效期（秒）
 */
@Data
public class LoginResponse {

    private String token;

    /** Token 有效期（秒），便于前端提示或倒计时 */
    private Long expiresIn;

    private User user;

    public LoginResponse(String token, Long expiresIn, User user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}
