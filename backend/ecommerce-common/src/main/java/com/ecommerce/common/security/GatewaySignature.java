package com.ecommerce.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 网关到业务服务的身份头签名工具（HMAC-SHA256）。
 *
 * <p>用途：业务服务在 {@link TrustedAuthenticationFilter} 中校验身份头确实来自网关，
 * 而不是客户端伪造。网关侧对 {@code userId|username|role|timestamp} 签名，
 * 业务服务用同一密钥重算并做定长比较。
 *
 * <p>说明：签名校验由配置开关 {@code security.trusted-header.verify-signature} 控制，
 * 默认关闭以便本地直连 8081/8082/8083 调试；容器化或对外部署时必须开启并配合网络隔离。
 */
public final class GatewaySignature {

    /** 请求头：签名时间戳（秒） */
    public static final String HEADER_GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";
    /** 请求头：身份头签名 */
    public static final String HEADER_GATEWAY_SIGNATURE = "X-Gateway-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private GatewaySignature() {
    }

    /** 拼装被签名的原文，顺序必须与 {@link #verify} 一致 */
    public static String buildPayload(Long userId, String username, String role, long timestamp) {
        return userId + "|" + username + "|" + (role == null ? "" : role) + "|" + timestamp;
    }

    /** 计算签名（小写十六进制） */
    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 密钥缺失或算法不可用属于部署配置错误，必须暴露而不是静默放行
            throw new IllegalStateException("网关身份头签名计算失败，请检查 jwt.secret 配置", e);
        }
    }

    /** 定长比较校验签名，避免时序侧信道 */
    public static boolean verify(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(secret, payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }
}
