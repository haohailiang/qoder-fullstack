package com.ecommerce.order.service.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付宝签名/验签测试。
 *
 * <p>不使用打桩：测试内用 {@link KeyPairGenerator} 生成 RSA 2048 密钥对，
 * 通过官方 SDK 签名后交给 {@link AlipaySignature#rsaCheckV1} 校验，
 * 保证回调链路上的验签逻辑与真实支付宝环境一致。
 */
@DisplayName("支付宝签名与验签")
class AlipaySignatureTest {

    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";

    private static String privateKeyBase64;
    private static String publicKeyBase64;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /** 构造一份典型的异步通知参数（不含 sign，便于签名后回填） */
    private static Map<String, String> buildNotifyParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", "2021000000000000");
        params.put("out_trade_no", "ORD20250101120000ABCDEF");
        params.put("trade_no", "2025010122001440000000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "199.98");
        params.put("subject", "测试商品");
        return params;
    }

    /**
     * 按支付宝规则签名：签名原文由 SDK 的 {@code getSignContent} 生成，
     * 与其在 {@code rsaCheckV1} 中使用的待验签原文保持一致。
     */
    private static void sign(Map<String, String> params) throws Exception {
        String content = AlipaySignature.getSignContent(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKeyBase64, CHARSET, SIGN_TYPE));
    }

    @Nested
    @DisplayName("rsaCheckV1 - 异步通知验签")
    class RsaCheck {

        @Test
        @DisplayName("未篡改：签名校验通过")
        void shouldPassWhenSignatureValid() throws Exception {
            Map<String, String> params = buildNotifyParams();
            sign(params);

            assertThat(AlipaySignature.rsaCheckV1(params, publicKeyBase64, CHARSET, SIGN_TYPE)).isTrue();
        }

        @Test
        @DisplayName("篡改金额：签名校验失败")
        void shouldFailWhenAmountTampered() throws Exception {
            Map<String, String> params = buildNotifyParams();
            sign(params);
            params.put("total_amount", "0.01");

            assertThat(AlipaySignature.rsaCheckV1(params, publicKeyBase64, CHARSET, SIGN_TYPE)).isFalse();
        }

        @Test
        @DisplayName("签名缺失：校验不通过（SDK 抛异常）")
        void shouldFailWhenSignMissing() {
            Map<String, String> params = buildNotifyParams();

            assertThatThrownBy(() -> AlipaySignature.rsaCheckV1(params, publicKeyBase64, CHARSET, SIGN_TYPE))
                    .isInstanceOf(AlipayApiException.class);
        }

        @Test
        @DisplayName("用另一对密钥签名的回调：校验失败")
        void shouldFailWhenSignedByOtherKey() throws Exception {
            Map<String, String> params = buildNotifyParams();
            sign(params);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String otherPublicKey = Base64.getEncoder()
                    .encodeToString(generator.generateKeyPair().getPublic().getEncoded());

            assertThat(AlipaySignature.rsaCheckV1(params, otherPublicKey, CHARSET, SIGN_TYPE)).isFalse();
        }
    }
}