package com.ecommerce.common.config;

import com.ecommerce.common.Result;
import com.ecommerce.common.security.TrustedAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * 业务服务统一安全配置。
 *
 * <p>网关已完成 JWT 校验与 Redis 会话校验，各业务服务信任网关请求头，因此 URL 层面放行所有请求，
 * 由 {@link TrustedAuthenticationFilter} 从请求头提取用户身份与角色写入 SecurityContext，
 * 方法级权限再由 {@code @EnableMethodSecurity} 驱动的 {@code @PreAuthorize} 在管理接口上生效。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TrustedAuthenticationFilter trustedAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TrustedAuthenticationFilter trustedAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.trustedAuthenticationFilter = trustedAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED,
                                        Result.error(401, "unauthorized")))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpStatus.FORBIDDEN,
                                        Result.error(403, "access denied"))))
                .addFilterBefore(trustedAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
