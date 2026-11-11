package com.ecommerce.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 用户服务直连场景的 CORS 配置（默认关闭）。
 *
 * <p>CORS 已收敛到网关（ecommerce-gateway 的 globalcors 白名单）单层管理：
 * 前端统一经 Vite 代理 / nginx / 网关访问后端，不会触发跨域。
 * 此处保留仅用于开发时浏览器直接跨域访问 8081（如 Swagger 联调），
 * 需要时设置 {@code CORS_DIRECT_ACCESS_ENABLED=true} 开启；
 * 开启后仍是 {@code allowedOriginPatterns=*} + allowCredentials 的组合，不得用于对外部署。
 * <p>
 * 引入 Spring Security 后改为暴露 CorsConfigurationSource Bean，
 * 由 SecurityConfig 中的 http.cors() 统一在安全过滤链 early stage 应用，
 * 避免与 WebMvcConfigurer 方式叠加导致响应头重复（Access-Control-Allow-Origin 多值报错）。
 */
@Configuration
@ConditionalOnProperty(name = "cors.direct-access.enabled", havingValue = "true")
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
