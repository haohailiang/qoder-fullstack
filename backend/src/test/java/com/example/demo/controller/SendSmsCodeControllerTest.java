package com.example.demo.controller;

import com.example.demo.common.BusinessException;
import com.example.demo.service.CaptchaService;
import com.example.demo.service.SmsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录 / 注册页「发送短信验证码」接口（POST /auth/send-sms-code，permitAll）集成测试。
 * <p>
 * 通过 @MockBean 关闭真实的图片验证码与短信服务，聚焦接口的「先图片验证码、后发送」编排：
 * - 图片验证码校验不通过：短信不发送（sendVerifyCode never），返回统一 Result 错误（HTTP 200 + body code 400）
 * - 图片验证码通过：按注册场景传入的 phone 调用 sendVerifyCode，返回 200
 * - 同一手机号频控：SmsService 抛出 code=429 业务异常，经 GlobalExceptionHandler 转为 body code 429
 * 参数校验（captchaKey / captchaValue 必填）也走 @Valid → 400 分支。
 * <p>
 * 与 AuthControllerExceptionTest 一样，GlobalExceptionHandler 处理后的 HTTP 状态恒为 200，错误码在 body.code。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SendSmsCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private SmsService smsService;

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/auth/send-sms-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("图片验证码校验不通过：短信不发送，返回 400")
    void captchaInvalidBlocksSms() throws Exception {
        doThrow(new BusinessException(400, "验证码错误或已过期，请重新获取"))
                .when(captchaService).validate(any(), any());

        send("{\"captchaKey\":\"k\",\"captchaValue\":\"bad\",\"phone\":\"13800000001\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码错误或已过期，请重新获取"));

        verify(smsService, never()).sendVerifyCode(anyString());
    }

    @Test
    @DisplayName("图片验证码通过：按注册手机号发送短信验证码，返回 200")
    void captchaValidSendsSms() throws Exception {
        doNothing().when(captchaService).validate(any(), any());

        send("{\"captchaKey\":\"k\",\"captchaValue\":\"ok\",\"phone\":\"13800000002\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(smsService, times(1)).sendVerifyCode("13800000002");
    }

    @Test
    @DisplayName("同一手机号频控：SmsService 抛 429，返回统一 Result（body code 429）")
    void rateLimitedReturns429() throws Exception {
        doNothing().when(captchaService).validate(any(), any());
        doThrow(new BusinessException(429, "验证码发送过于频繁，请稍后再试"))
                .when(smsService).sendVerifyCode(anyString());

        send("{\"captchaKey\":\"k\",\"captchaValue\":\"ok\",\"phone\":\"13800000003\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("验证码发送过于频繁，请稍后再试"));
    }

    @Test
    @DisplayName("参数校验：缺少图片验证码字段返回 400")
    void missingCaptchaParam() throws Exception {
        send("{\"phone\":\"13800000004\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
