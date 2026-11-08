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

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-minutes:120}") long expirationMinutes) {
        // HS256 要求密钥长度 >= 32 字节，配置过短时这里直接失败提醒
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }

    /**
     * 生成 Token：subject 存 username，附加 uid claim 与过期时间
     */
    public String generateToken(String username, Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim(CLAIM_USER_ID, userId)
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
