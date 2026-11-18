package com.ecommerce.product.config;

import com.ecommerce.product.config.properties.OssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 相关配置入口：注册 {@link OssProperties}。
 *
 * <p>OSS 客户端由 {@link OssClientProvider} 懒加载创建，AK/SK 未配置时应用仍可正常启动，
 * 只有真正调用上传接口时才返回“OSS 未配置”的业务错误。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {
}
