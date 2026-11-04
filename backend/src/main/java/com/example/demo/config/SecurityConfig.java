package com.example.demo.config;

import com.example.demo.common.Result;
import com.example.demo.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 配置：
 * - 无状态 JWT 认证，关闭 CSRF 与 Session
 * - 注册、登录、健康检查、静态资源匿名访问，其余接口需认证
 * - CORS 通过 CorsConfigurationSource Bean（见 CorsConfig）+ http.cors() 生效，
 *   未授权 / 权限不足时也返回项目统一的 Result JSON 结构
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                    // 使用容器中的 CorsConfigurationSource Bean（CorsConfig 中定义）
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求放行
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // 匿名可访问：注册、登录、图片验证码、发送短信验证码（登录前即可调用，依赖图片验证码防滥发）、健康检查、基础静态资源
                        .requestMatchers("/auth/register", "/auth/login", "/auth/captcha", "/auth/send-sms-code").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
                        // 其余接口（含用户 CRUD、/auth/me、/auth/logout）默认需要认证
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // 未携带 Token 访问受保护接口
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED,
                                        Result.error(401, "未授权访问，请先登录")))
                        // 已认证但权限不足
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpStatus.FORBIDDEN,
                                        Result.error(403, "未授权访问：权限不足"))))
                // JWT 过滤器放在用户名密码认证过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 以项目统一 Result 结构写出安全异常响应 */
    private void writeJson(HttpServletResponse response, HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
