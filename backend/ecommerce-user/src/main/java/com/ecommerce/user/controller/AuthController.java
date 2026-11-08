package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.SendSmsCodeRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.RedisSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 
 * <p>登出时会清除 Redis 中存储的会话 Token，确保该设备立即失效。
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户注册、登录、登出、验证码等认证相关接口")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RedisSessionService redisSessionService;

    public AuthController(AuthService authService,
                          RedisSessionService redisSessionService) {
        this.authService = authService;
        this.redisSessionService = redisSessionService;
    }

    /** 注册 */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户账号，需要提供用户名、密码、姓名、邮箱、手机号等信息")
    public Result<User> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /** 登录：返回 JWT 与用户基本信息 */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT Token 和用户信息")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 登录 / 注册页发送短信验证码（匿名可访问）。
     * 请求体携带图片验证码 key/value + 手机号（注册）或用户名（登录）；
     * 后端先校验图片验证码，通过后才真正发送，依赖图片验证码 + 同一手机号 60 秒频控防滥发。
     */
    @PostMapping("/send-sms-code")
    @Operation(summary = "发送短信验证码", description = "发送短信验证码，需要先通过图片验证码校验")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        authService.sendSmsCode(request);
        return Result.success();
    }

    /** 获取当前登录用户（需认证） */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的详细信息")
    public Result<User> me(Authentication authentication) {
        return Result.success(authService.getCurrentUser(authentication.getName()));
    }

    /**
     * 退出登录。
     * 清除 Redis 中存储的会话 Token，使当前设备立即失效。
     * 前端也应同步清除本地 localStorage 中的 Token 和用户信息。
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "清除 Redis 中的会话 Token，使当前设备立即失效")
    public Result<Void> logout(Authentication authentication) {
        // 获取当前登录用户信息
        User user = authService.getCurrentUser(authentication.getName());
        // 清除 Redis 中的会话 Token
        redisSessionService.removeToken(user.getId());
        log.info("用户 {} 已退出登录，Redis 会话已清除", user.getUsername());
        return Result.success();
    }
}
