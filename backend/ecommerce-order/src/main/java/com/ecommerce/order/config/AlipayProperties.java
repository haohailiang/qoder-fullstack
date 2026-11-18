package com.ecommerce.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱支付配置。
 *
 * <p>所有敏感参数（appId / 应用私钥 / 支付宝公钥）一律通过环境变量注入，
 * 配置项默认空值，未配置时服务仍可正常启动，创建支付时返回业务码 503。
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 沙箱应用 APPID */
    private String appId;

    /** 应用私钥（PKCS8），支持单行 Base64、含 \n 转义或带 PEM 头尾 */
    private String privateKey;

    /** 支付宝公钥（沙箱控制台「支付宝公钥」，非应用公钥） */
    private String alipayPublicKey;

    /** 网关地址，默认支付宝沙箱网关 */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 异步通知地址（必须是支付宝服务器可达的公网地址，可为空：为空时仅依赖主动查询） */
    private String notifyUrl;

    /** 同步回跳地址（本站支付结果页） */
    private String returnUrl;

    /** 签名算法，沙箱默认 RSA2 */
    private String signType = "RSA2";

    /** 报文编码 */
    private String charset = "UTF-8";

    /** 订单支付超时时间，超时后支付宝侧交易关闭 */
    private String timeoutExpress = "30m";

    /** 是否已配置完整的支付凭据 */
    public boolean isConfigured() {
        return hasText(appId) && hasText(privateKey) && hasText(alipayPublicKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}