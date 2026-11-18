package com.ecommerce.product.config;

import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.impl.LocalFileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 本地兜底存储的静态资源映射。
 *
 * <p>把 {@code oss.local-dir} 目录挂到 {@code /files/local/**}，配合网关的
 * {@code /api/product/files/local} 白名单，浏览器可直接用相对 URL 加载已上传图片。
 */
@Configuration
public class LocalFileStorageConfig implements WebMvcConfigurer {

    private final OssProperties properties;

    public LocalFileStorageConfig(OssProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = LocalFileStorageService.resolveRoot(properties);
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/local/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
