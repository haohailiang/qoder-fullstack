package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.config.properties.SmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmsService 单元测试（Mock RestTemplate，不发起真实网络请求，不依赖 SpringBootTest / 数据库）：
 * 覆盖 sms.enabled=false 跳过真实调用、同一手机号频控、网络异常 / 5xx 触发重试、
 * 4xx 不重试直接失败等分支。每个用例都使用不同手机号，避免频控状态在用例间相互干扰。
 */
class SmsServiceTest {

    private RestTemplate restTemplate;
    private SmsProperties properties;
    private SmsService smsService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setRateLimitSeconds(60);
        properties.setMaxRetry(3);
        properties.setRetryIntervalMs(1L); // 测试时几乎不等待，保证用例快速执行
        properties.setRetryBackoff("linear");
        properties.setApiUrl("https://sms.example.com/send.json");
        properties.setAccountSid("sid");
        properties.setApiKey("key");
        properties.setTemplateId("1");
        smsService = new SmsService(properties, restTemplate);
    }

    @Test
    @DisplayName("sms.enabled=false 时不调用真实云片网接口，但仍执行频控记录")
    void disabledSkipsHttpCall() {
        properties.setEnabled(false);

        assertThatCode(() -> smsService.sendVerifyCode("13800000001")).doesNotThrowAnyException();

        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("频控：同一手机号 60 秒内第二次发送抛出 code=429 业务异常")
    void rateLimitBlocksSecondSendWithinWindow() {
        when(restTemplate.postForObject(anyString(), any(), any())).thenReturn("ok");

        smsService.sendVerifyCode("13800000002");

        assertThatThrownBy(() -> smsService.sendVerifyCode("13800000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 429)
                .hasMessage("验证码发送过于频繁，请稍后再试");

        // 第二次调用被频控拦截，不应再次触碰真实接口
        verify(restTemplate, times(1)).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("网络异常 / 超时触发重试，达到 maxRetry 后仍失败则抛出 code=502")
    void retriesOnNetworkErrorThenFails() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> smsService.sendVerifyCode("13800000003"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 502)
                .hasMessage("短信发送失败，请稍后重试");

        verify(restTemplate, times(3)).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("5xx 服务端错误触发重试，重试成功后正常返回不抛异常")
    void retriesOnServerErrorThenSucceeds() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(mock(HttpServerErrorException.class))
                .thenReturn("ok");

        assertThatCode(() -> smsService.sendVerifyCode("13800000004")).doesNotThrowAnyException();

        verify(restTemplate, times(2)).postForObject(anyString(), any(), any());
    }

    @Test
    @DisplayName("4xx 客户端错误（如 apiKey 错误）不重试，直接失败")
    void doesNotRetryOnClientError() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(mock(HttpClientErrorException.class));

        assertThatThrownBy(() -> smsService.sendVerifyCode("13800000005"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 502)
                .hasMessage("短信发送失败，请稍后重试");

        verify(restTemplate, times(1)).postForObject(anyString(), any(), any());
    }
}
