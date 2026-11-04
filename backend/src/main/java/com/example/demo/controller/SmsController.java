package com.example.demo.controller;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.dto.SendSmsCodeRequest;
import com.example.demo.entity.User;
import com.example.demo.service.SmsService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短信发送接口。
 * 实际访问路径为 POST /api/sms/send-code（/api 来自 server.servlet.context-path 配置），
 * 需登录访问（走 SecurityConfig 默认的 authenticated() 规则，供前端用户管理页调用）。
 * <p>
 * 前端不直接对接云片网 API，apiKey 等敏感配置只留在后端 {@link SmsService} 内部使用；
 * 后端按手机号做发送频率控制（见 sms.rate-limit-seconds），避免短信滥发。
 */
@RestController
@RequestMapping("/sms")
public class SmsController {

    private final UserService userService;
    private final SmsService smsService;

    public SmsController(UserService userService, SmsService smsService) {
        this.userService = userService;
        this.smsService = smsService;
    }

    /** 根据用户 id 找到其手机号，发送短信验证码 */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendSmsCodeRequest request) {
        User user = userService.getUser(request.getUserId());
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(400, "该用户未设置手机号，无法发送验证码");
        }
        smsService.sendVerifyCode(user.getPhone());
        return Result.success();
    }
}
