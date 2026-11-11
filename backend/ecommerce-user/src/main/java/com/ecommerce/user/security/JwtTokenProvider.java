package com.ecommerce.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成 / 解析 / 校验 Token（HS256 签名）
 */
@Component
public class JwtTokenProvider {

    /** Token 载荷中存放用户 id 的 claim 名 */
    public static final String CLAIM_USER_ID = "uid";

    /** Token 载荷中存放角色的 claim 名（网关读取后注入 X-User-Role） */
    public static final String CLAIM_ROLE = "role";

    /** 无角色信息时的兜底角色 */
    private static final String DEFAULT_ROLE = "USER";

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-minutes:120}") long expirationMinutes) {
        // HS256 要求密钥长度 >= 32 字节，配置过短时这里直接失败提醒
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }

    /**
     * 生成 Token（无角色信息，默认 USER）
     */
    public String generateToken(String username, Long userId) {
        return generateToken(username, userId, DEFAULT_ROLE);
    }

    /**
     * 生成 Token：subject 存 username，附加 uid / role claim 与过期时间。
     * role 使网关无需回查用户表就能得到角色，并向下游注入 X-User-Role。
     */
    public String generateToken(String username, Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, (role == null || role.isBlank()) ? DEFAULT_ROLE : role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMillis))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token，返回载荷；签名不合法抛 JwtException，过期抛 ExpiredJwtException
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 Token 是否有效（签名正确且未过期）
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Token 有效期（秒），用于返回给前端展示或提示 */
    public long getExpiresInSeconds() {
        return expirationMillis / 1000;
    }
}
