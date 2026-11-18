package com.ecommerce.order.service.alipay;

/**
 * 支付宝密钥归一化工具。
 *
 * <p>环境变量注入的密钥可能是单行 Base64、含字面量 {@code \n} 的字符串，
 * 或带 {@code -----BEGIN/END ...-----} 头尾的 PEM 内容；SDK 只接受纯 Base64，
 * 统一在此清洗，避免因格式差异导致签名失败。
 */
public final class AlipayKeyUtils {

    private static final String PEM_HEADER_PATTERN = "-----BEGIN [A-Z ]+-----";
    private static final String PEM_FOOTER_PATTERN = "-----END [A-Z ]+-----";
    private static final String WHITESPACE_PATTERN = "\\s";

    private AlipayKeyUtils() {
    }

    /**
     * 归一化密钥：去 PEM 头尾、还原换行转义、去空白字符。
     *
     * @param raw 原始密钥文本
     * @return 纯 Base64 密钥；入参为 null 时返回 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\n", "\n")
                .replaceAll(PEM_HEADER_PATTERN, "")
                .replaceAll(PEM_FOOTER_PATTERN, "")
                .replaceAll(WHITESPACE_PATTERN, "");
    }
}
