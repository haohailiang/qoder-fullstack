package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.config.properties.CaptchaProperties;
import com.example.demo.dto.CaptchaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CaptchaService 单元测试（不依赖 SpringBootTest / 数据库）：
 * 覆盖生成、正确校验通过、一次性使用（成功/失败都会消费同一个 key）、过期、
 * 不匹配、captcha.enabled=false 时跳过校验等分支。
 * 借助同包可见的 saveForTest 直接构造已知验证码，避免依赖随机生成结果。
 */
class CaptchaServiceTest {

    private CaptchaProperties properties;
    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        properties = new CaptchaProperties();
        properties.setEnabled(true);
        properties.setExpireSeconds(120);
        properties.setCodeLength(4);
        properties.setWidth(130);
        properties.setHeight(44);
        properties.setLineCount(5);
        captchaService = new CaptchaService(properties);
    }

    @Test
    @DisplayName("generate 返回非空 captchaKey 与 Base64 PNG 图片")
    void generateReturnsKeyAndImage() {
        CaptchaResponse response = captchaService.generate();

        assertThat(response.getCaptchaKey()).isNotBlank();
        assertThat(response.getCaptchaImage()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("validate 校验通过（忽略大小写）")
    void validateCorrectCode() {
        captchaService.saveForTest("key-1", "AB3d", System.currentTimeMillis() + 60_000L);

        assertThatCode(() -> captchaService.validate("key-1", "ab3D")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("一次性使用：同一 key 校验通过后再次校验必然失败")
    void validateIsOneTimeUseAfterSuccess() {
        captchaService.saveForTest("key-2", "AB3d", System.currentTimeMillis() + 60_000L);

        captchaService.validate("key-2", "AB3d");

        assertThatThrownBy(() -> captchaService.validate("key-2", "AB3d"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessage("验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("一次性使用：校验失败（不匹配）同样会消费该 key，不允许重试同一个 key")
    void validateWrongCodeConsumesKey() {
        captchaService.saveForTest("key-3", "ABCD", System.currentTimeMillis() + 60_000L);

        assertThatThrownBy(() -> captchaService.validate("key-3", "WRONG"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400);

        // key 已被消费，即使这次传入正确码也应该失败
        assertThatThrownBy(() -> captchaService.validate("key-3", "ABCD"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("validate 过期码：已过期条目校验失败")
    void validateExpiredCode() {
        captchaService.saveForTest("key-4", "ABCD", System.currentTimeMillis() - 1_000L);

        assertThatThrownBy(() -> captchaService.validate("key-4", "ABCD"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误或已过期，请重新获取");
    }

    @Test
    @DisplayName("validate key/value 缺失：抛出参数提示异常")
    void validateBlankInput() {
        assertThatThrownBy(() -> captchaService.validate(null, "ABCD"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入验证码");
        assertThatThrownBy(() -> captchaService.validate("key-5", "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入验证码");
    }

    @Test
    @DisplayName("captcha.enabled=false 时跳过校验，不抛异常")
    void validateSkippedWhenDisabled() {
        properties.setEnabled(false);

        assertThatCode(() -> captchaService.validate("not-exist-key", "whatever")).doesNotThrowAnyException();
    }
}
