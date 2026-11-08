package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import com.ecommerce.user.dto.CaptchaResponse;
import com.ecommerce.user.service.CaptchaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片验证码接口，供登录 / 注册页获取验证码图片。
 * 实际访问路径为 GET /api/auth/captcha（/api 来自 server.servlet.context-path 配置），
 * 已在 SecurityConfig 中配置为 permitAll（登录、注册页尚未认证，需要匿名访问）。
 */
@RestController
@RequestMapping("/auth")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /** 生成一张新的图片验证码 */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        return Result.success(captchaService.generate());
    }
}
