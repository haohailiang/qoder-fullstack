package com.ecommerce.user.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.config.properties.SmsProperties;
import com.ecommerce.user.dto.SmsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("sendVerifyCode 存储验证码可用正确码校验通过（enabled=false 仍生成存储）")
    void sendVerifyCodeStoresValidCode() {
        properties.setEnabled(false);
        String phone = "13800000101";

        smsService.sendVerifyCode(phone);

        // 从发送记录取出本次生成的明文验证码，校验应当通过
        String code = latestRecord(phone).getCode();
        assertThat(code).hasSize(6);
        assertThatCode(() -> smsService.validateCode(phone, code)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("短信验证码校验：错误码抛出 code=400")
    void validateCodeWrong() {
        smsService.saveCodeForTest("13800000102", "123456", System.currentTimeMillis() + 60_000L);

        assertThatThrownBy(() -> smsService.validateCode("13800000102", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessage("短信验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("短信验证码校验：一次性使用，同一手机号校验通过后再次校验必然失败")
    void validateCodeIsOneTimeUse() {
        smsService.saveCodeForTest("13800000103", "123456", System.currentTimeMillis() + 60_000L);

        smsService.validateCode("13800000103", "123456");

        assertThatThrownBy(() -> smsService.validateCode("13800000103", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("短信验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("短信验证码校验：失败（不匹配）同样会消费该手机号验证码，不允许重试同一个码")
    void validateCodeWrongConsumesEntry() {
        smsService.saveCodeForTest("13800000104", "123456", System.currentTimeMillis() + 60_000L);

        assertThatThrownBy(() -> smsService.validateCode("13800000104", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400);

        // 错误校验已消费，即使再传正确码也应失败
        assertThatThrownBy(() -> smsService.validateCode("13800000104", "123456"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("短信验证码校验：已过期条目校验失败")
    void validateCodeExpired() {
        smsService.saveCodeForTest("13800000105", "123456", System.currentTimeMillis() - 1_000L);

        assertThatThrownBy(() -> smsService.validateCode("13800000105", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("短信验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("短信验证码校验：手机号 / 验证码缺失抛出提示异常")
    void validateCodeBlankInput() {
        assertThatThrownBy(() -> smsService.validateCode(null, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入短信验证码");
        assertThatThrownBy(() -> smsService.validateCode("13800000106", "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入短信验证码");
    }

    @Test
    @DisplayName("发送记录：enabled=false 成功发送后记录手机号 / 明文验证码 / SUCCESS 状态")
    void recordsSuccessOnDisabledSend() {
        properties.setEnabled(false);
        String phone = "13800000107";

        smsService.sendVerifyCode(phone);

        SmsRecord record = latestRecord(phone);
        assertThat(record.getPhone()).isEqualTo(phone);
        assertThat(record.getCode()).hasSize(6);
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(record.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("发送记录：真实发送失败（重试耗尽）仍记录一条 FAILED")
    void recordsFailureOnSendError() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new ResourceAccessException("connect timed out"));
        String phone = "13800000108";

        assertThatThrownBy(() -> smsService.sendVerifyCode(phone))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 502);

        assertThat(latestRecord(phone).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("发送记录：按发送时间倒序，最新记录在前")
    void recordsOrderedNewestFirst() {
        properties.setEnabled(false);
        smsService.sendVerifyCode("13800000109");
        smsService.sendVerifyCode("13800000110");

        List<SmsRecord> all = smsService.getRecords();
        assertThat(all).isNotEmpty();
        assertThat(all.get(0).getPhone()).isEqualTo("13800000110");
    }

    /** 从发送记录中取指定手机号最新一条（记录本身已按倒序存放） */
    private SmsRecord latestRecord(String phone) {
        return smsService.getRecords().stream()
                .filter(r -> phone.equals(r.getPhone()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到手机号 " + phone + " 的发送记录"));
    }
}
