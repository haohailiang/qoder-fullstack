package com.ecommerce.gateway.filter;

import com.ecommerce.common.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局鉴权过滤器：
 * <ol>
 *   <li>白名单路径直接放行（登录、注册、验证码、健康检查）</li>
 *   <li>从 Authorization: Bearer token 提取 JWT</li>
 *   <li>校验 JWT 签名与有效期</li>
 *   <li>校验通过后，将 userId / username 写入请求头传递给下游微服务</li>
 *   <li>校验失败直接返回 401 JSON 响应</li>
 * </ol>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";

    /** 白名单路径：这些路径不需要 JWT 认证 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/auth/login",
            "/api/user/auth/register",
            "/api/user/auth/captcha",
            "/api/user/auth/send-sms-code",
            "/api/user/health"
    );

    /** 白名单前缀：匹配这些前缀的路径不需要 JWT 认证（Swagger UI / OpenAPI 文档） */
    private static final List<String> WHITE_PREFIX_LIST = List.of(
            "/api/user/swagger-ui",
            "/api/user/v3/api-docs",
            "/api/product/swagger-ui",
            "/api/product/v3/api-docs",
            "/api/order/swagger-ui",
            "/api/order/v3/api-docs",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单路径直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        // 提取 Authorization 头
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "未授权访问，请先登录"));
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String username = claims.getSubject();
            Long userId = claims.get("uid", Long.class);

            if (userId == null) {
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                        Result.error(401, "Token 信息不完整"));
            }

            // 将用户信息写入请求头，传递给下游微服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HEADER_USER_ID, String.valueOf(userId))
                    .header(HEADER_USER_NAME, username)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "Token 已过期，请重新登录"));
        } catch (JwtException e) {
            log.warn("Token 无效: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "Token 无效"));
        } catch (Exception e) {
            log.error("鉴权异常: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "认证失败"));
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhiteListed(String path) {
        if (WHITE_LIST.stream().anyMatch(path::equals)) {
            return true;
        }
        return WHITE_PREFIX_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, Result<Void> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("序列化错误响应失败", e);
            return response.setComplete();
        }
    }
}
