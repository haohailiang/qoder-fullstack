package com.ecommerce.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 全局 CORS 配置：允许前端跨域访问后端接口。
 * 开发环境下前端经 Vite 代理访问时不会触发跨域，此处配置用于直连场景。
 * <p>
 * 引入 Spring Security 后改为暴露 CorsConfigurationSource Bean，
 * 由 SecurityConfig 中的 http.cors() 统一在安全过滤链 early stage 应用，
 * 避免与 WebMvcConfigurer 方式叠加导致响应头重复（Access-Control-Allow-Origin 多值报错）。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
