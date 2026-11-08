package com.ecommerce.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 仓库配置 - 仅在启用 ES 时生效
 * 默认禁用，设置 spring.data.elasticsearch.enabled=true 启用
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@EnableElasticsearchRepositories(basePackages = "com.ecommerce.product.elasticsearch")
public class ElasticsearchConfig {
}
