package com.ecommerce.common.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 可信认证过滤器：从网关传入的请求头中提取用户身份与角色并写入 SecurityContext。
 *
 * <p>网关（ecommerce-gateway）已完成 JWT 签名校验与 Redis 会话校验，并向下游注入
 * {@code X-User-Id} / {@code X-User-Name} / {@code X-User-Role} 及对应的 HMAC 签名头。
 *
 * <p>{@code security.trusted-header.verify-signature=true} 时还会校验签名与时间戳，
 * 拒绝客户端绕过网关伪造身份头；默认关闭以保持本地直连 8081/8082/8083 调试能力
 * （开发环境接受的已知取舍），容器化或对外部署时必须开启并配合网络隔离。
 */
@Component
public class TrustedAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrustedAuthenticationFilter.class);

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 无角色信息时的兜底角色，与历史行为保持一致 */
    private static final String DEFAULT_ROLE = "USER";

    /** 是否校验网关签名（默认关闭，便于本地直连调试） */
    @Value("${security.trusted-header.verify-signature:false}")
    private boolean verifySignature;

    /** 网关签名密钥，与网关 jwt.secret 同一份配置 */
    @Value("${jwt.secret:}")
    private String gatewaySecret;

    /** 签名时间戳允许的最大偏差（秒），防重放 */
    @Value("${security.trusted-header.max-age-seconds:300}")
    private long maxAgeSeconds;

    @PostConstruct
    void checkConfiguration() {
        if (verifySignature && (gatewaySecret == null || gatewaySecret.isBlank())) {
            throw new IllegalStateException(
                    "security.trusted-header.verify-signature=true 但 jwt.secret 未配置：无法校验网关身份头签名");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        String username = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);

        if (userId != null && username != null) {
            if (verifySignature && !isTrusted(request, userId, username, role)) {
                // 开关开启后，只有携带合法网关签名的请求才能获得身份
                log.warn("身份头签名校验失败，按匿名请求处理: uri={}", request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "身份凭据无效");
                return;
            }
            String effectiveRole = (role == null || role.isBlank()) ? DEFAULT_ROLE : role;
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Trusted auth set: username={}, userId={}, role={}", username, userId, effectiveRole);
        }

        filterChain.doFilter(request, response);
    }

    /** 校验网关 HMAC 签名与时间戳新鲜度 */
    private boolean isTrusted(HttpServletRequest request, String userId, String username, String role) {
        String signature = request.getHeader(GatewaySignature.HEADER_GATEWAY_SIGNATURE);
        String timestampHeader = request.getHeader(GatewaySignature.HEADER_GATEWAY_TIMESTAMP);
        if (signature == null || timestampHeader == null) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
            // 网关未传角色时按默认角色签名，保持与 forwardWithIdentity 的拼接顺序一致
            String effectiveRole = (role == null || role.isBlank()) ? DEFAULT_ROLE : role;
            String payload = GatewaySignature.buildPayload(Long.valueOf(userId), username, effectiveRole, timestamp);
            if (!GatewaySignature.verify(gatewaySecret, payload, signature)) {
                return false;
            }
            long skew = Math.abs(System.currentTimeMillis() / 1000 - timestamp);
            if (skew > maxAgeSeconds) {
                log.warn("网关签名时间戳过期: skew={}秒", skew);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            log.warn("网关签名头格式非法: userId={}, timestamp={}", userId, timestampHeader);
            return false;
        }
    }
}
