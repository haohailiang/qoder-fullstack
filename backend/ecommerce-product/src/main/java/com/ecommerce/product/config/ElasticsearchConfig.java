package com.ecommerce.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 配置 - 仅在启用 ES 时生效
 * 默认禁用，设置 spring.elasticsearch.enabled=true 启用
 * 仓库启用由 Spring Boot 自动配置处理（spring-boot-starter-data-elasticsearch）
 */
@Configuration
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfig {
}
