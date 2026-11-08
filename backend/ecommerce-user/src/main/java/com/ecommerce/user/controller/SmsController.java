package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import com.ecommerce.user.dto.SmsRecord;
import com.ecommerce.user.service.SmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短信发送记录查询接口。
 * 实际访问路径为 GET /api/sms/records（/api 来自 server.servlet.context-path 配置），
 * 需登录访问（走 SecurityConfig 默认的 authenticated() 规则，供前端用户管理页调用）。
 * <p>
 * 返回进程内内存保存的全部短信验证码发送记录（手机号 / 验证码 / 发送时间 / 状态），按发送时间倒序。
 * 发送动作本身已迁移到匿名接口 POST /api/auth/send-sms-code（由图片验证码防滥发），本控制器只负责查询。
 */
@RestController
@RequestMapping("/sms")
@Tag(name = "短信服务", description = "短信发送记录查询接口")
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    /** 查询所有短信发送记录，按发送时间倒序（最新在前） */
    @GetMapping("/records")
    @Operation(summary = "查询短信发送记录", description = "查询所有短信验证码发送记录，按发送时间倒序")
    public Result<List<SmsRecord>> records() {
        return Result.success(smsService.getRecords());
    }
}
