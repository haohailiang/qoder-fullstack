package com.ecommerce.product.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.properties.OssProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OSS 客户端懒加载提供者。
 *
 * <p>不在启动阶段创建客户端，保证 AK/SK 未配置时（本地开发、单元测试、CI）应用上下文仍能正常启动；
 * 首次上传/删除时才校验配置并建立连接，配置缺失返回 503 业务码。
 */
@Component
public class OssClientProvider {

    private static final Logger log = LoggerFactory.getLogger(OssClientProvider.class);

    private final OssProperties properties;

    private volatile OSS client;

    public OssClientProvider(OssProperties properties) {
        this.properties = properties;
    }

    /** 获取客户端；未配置 AK/SK 或 bucket 时抛业务异常 */
    public OSS getClient() {
        if (!properties.isConfigured()) {
            throw new BusinessException(503, "OSS 未配置，请联系管理员");
        }
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new OSSClientBuilder()
                            .build(properties.getEndpoint(),
                                    properties.getAccessKeyId(),
                                    properties.getAccessKeySecret());
                    client = current;
                    log.info("OSS 客户端初始化完成: endpoint={}, bucket={}",
                            properties.getEndpoint(), properties.getBucket());
                }
            }
        }
        return current;
    }

    @PreDestroy
    public void close() {
        OSS current = client;
        if (current != null) {
            current.shutdown();
            client = null;
            log.info("OSS 客户端已关闭");
        }
    }
}
