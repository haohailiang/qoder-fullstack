package com.ecommerce.order.service.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.ecommerce.order.config.AlipayProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝客户端工厂：按需懒加载，未配置凭据时返回 null。
 *
 * <p>不在启动阶段创建客户端，保证未配置支付宝参数时订单服务仍能正常启动，
 * 也不影响其他下单路径。
 */
@Component
public class AlipayClientFactory {

    private final AlipayProperties properties;

    private volatile AlipayClient client;

    public AlipayClientFactory(AlipayProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取支付宝客户端。
     *
     * @return 未配置凭据时返回 null
     */
    public AlipayClient getClient() {
        if (!properties.isConfigured()) {
            return null;
        }
        AlipayClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new DefaultAlipayClient(
                            properties.getGatewayUrl(),
                            properties.getAppId(),
                            AlipayKeyUtils.normalize(properties.getPrivateKey()),
                            "json",
                            properties.getCharset(),
                            AlipayKeyUtils.normalize(properties.getAlipayPublicKey()),
                            properties.getSignType());
                    client = local;
                }
            }
        }
        return local;
    }
}