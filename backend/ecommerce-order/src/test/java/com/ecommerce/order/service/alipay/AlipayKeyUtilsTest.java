package com.ecommerce.order.service.alipay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlipayKeyUtils 单元测试：验证不同来源的密钥文本（环境变量、配置文件）能被归一化为 SDK 可用的纯 Base64。
 */
@DisplayName("AlipayKeyUtils - 支付宝密钥归一化")
class AlipayKeyUtilsTest {

    private static final String BASE64_BODY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ";
    /** 每 64 字符换行的典型 PEM 内容 */
    private static final String WRAPPED_BODY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIB\nAQ";

    @Nested
    @DisplayName("normalize - 归一化")
    class Normalize {

        @Test
        @DisplayName("单行 Base64：原样返回")
        void shouldKeepSingleLineBase64() {
            assertThat(AlipayKeyUtils.normalize(BASE64_BODY)).isEqualTo(BASE64_BODY);
        }

        @Test
        @DisplayName("字面量 \\n 转义：替换为真实换行后去空白")
        void shouldRestoreEscapedNewlines() {
            String raw = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIB\\nAQ";

            assertThat(AlipayKeyUtils.normalize(raw)).isEqualTo(BASE64_BODY);
        }

        @Test
        @DisplayName("PEM 格式：去掉 BEGIN/END 头尾与所有换行")
        void shouldStripPemHeaderAndFooter() {
            String raw = "-----BEGIN PRIVATE KEY-----\n" + WRAPPED_BODY + "\n-----END PRIVATE KEY-----";

            assertThat(AlipayKeyUtils.normalize(raw)).isEqualTo("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ");
        }

        @Test
        @DisplayName("公钥 PEM 格式：同样支持")
        void shouldStripPublicKeyPem() {
            String raw = "-----BEGIN PUBLIC KEY-----\n" + BASE64_BODY + "\n-----END PUBLIC KEY-----";

            assertThat(AlipayKeyUtils.normalize(raw)).isEqualTo(BASE64_BODY);
        }

        @Test
        @DisplayName("首尾空白与缩进：全部剔除")
        void shouldTrimWhitespace() {
            String raw = "  \tMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ  \n";

            assertThat(AlipayKeyUtils.normalize(raw)).isEqualTo(BASE64_BODY);
        }

        @Test
        @DisplayName("入参为 null：返回 null（未配置场景）")
        void shouldReturnNullForNullInput() {
            assertThat(AlipayKeyUtils.normalize(null)).isNull();
        }
    }
}