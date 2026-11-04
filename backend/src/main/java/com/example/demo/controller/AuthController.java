package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.SendSmsCodeRequest;
import com.example.demo.entity.User;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * 实际访问路径为 /api/auth/**（/api 来自 server.servlet.context-path 配置）。
 * 其中 /auth/register、/auth/login、/auth/captcha 与 /auth/send-sms-code 匿名可访问，
 * /auth/me 与 /auth/logout 需要携带有效 JWT。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /** 登录：返回 JWT 与用户基本信息 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 登录 / 注册页发送短信验证码（匿名可访问）。
     * 请求体携带图片验证码 key/value + 手机号（注册）或用户名（登录）；
     * 后端先校验图片验证码，通过后才真正发送，依赖图片验证码 + 同一手机号 60 秒频控防滥发。
     */
    @PostMapping("/send-sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        authService.sendSmsCode(request);
        return Result.success();
    }

    /** 获取当前登录用户（需认证） */
    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        return Result.success(authService.getCurrentUser(authentication.getName()));
    }

    /**
     * 退出登录。
     * JWT 无状态，服务端不维护会话，此接口仅作为前端调用后的确认；
     * 真正的“退出”由前端清除本地 Token 完成。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
