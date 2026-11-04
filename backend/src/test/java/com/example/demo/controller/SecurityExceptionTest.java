package com.example.demo.controller;

import com.example.demo.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过安全过滤链验证认证/授权/JWT 相关异常：
 * - 未携带 Token 访问受保护接口 -> AuthenticationEntryPoint 返回 HTTP 401
 * - Token 格式非法 / 签名无效 -> JwtAuthenticationFilter 返回 HTTP 401 "Token 无效"
 * - Token 已过期 -> JwtAuthenticationFilter 返回 HTTP 401 "Token 已过期，请重新登录"
 * - 携带有效 Token 查询不存在的用户 -> 命中业务异常 404（经由 GlobalExceptionHandler，HTTP 200 + body code 404）
 *
 * 与 AuthControllerExceptionTest 不同，这里的 401 由过滤器/入口点直接写出真实 HTTP 状态，
 * 而非 GlobalExceptionHandler，因此断言 status 为 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("未认证：无 Token 访问受保护接口返回 HTTP 401")
    void accessProtectedWithoutToken() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未授权访问，请先登录"));
    }

    @Test
    @DisplayName("JWT 无效：格式非法/签名错误的 Token 返回 401")
    void accessWithInvalidToken() throws Exception {
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Token 无效"));
    }

    @Test
    @DisplayName("JWT 过期：已过期的 Token 返回 401")
    void accessWithExpiredToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        // 签发时间为 2 小时前、过期时间为 1 小时前，确保解析时必然抛 ExpiredJwtException
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .setSubject("zhangsan")
                .setIssuedAt(new Date(now.getTime() - 2 * 3600_000L))
                .setExpiration(new Date(now.getTime() - 3600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Token 已过期，请重新登录"));
    }

    @Test
    @DisplayName("业务异常：有效 Token 查询不存在用户返回 404（HTTP 200 + body code）")
    void getNonexistentUserWithValidToken() throws Exception {
        String token = tokenProvider.generateToken("zhangsan", 1L);

        mockMvc.perform(get("/users/99999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在: id=99999"));
    }
}
