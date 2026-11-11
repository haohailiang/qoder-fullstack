package com.ecommerce.gateway.filter;

import com.ecommerce.common.Result;
import com.ecommerce.common.security.GatewaySignature;
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
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
 *   <li>剥离客户端自带的身份头，防止伪造下游信任凭据</li>
 *   <li>内部服务间接口（扣库存、秒杀回滚）一律拒绝外部访问</li>
 *   <li>白名单路径直接放行（登录、注册、验证码、健康检查、Swagger）</li>
 *   <li>校验 JWT 签名与有效期</li>
 *   <li>比对 Redis 会话（{@code login:token:{uid}}），使登出与单设备互踢真正生效</li>
 *   <li>校验通过后注入 X-User-Id / X-User-Name / X-User-Role 及网关签名头传递给下游</li>
 *   <li>校验失败直接返回 401 JSON 响应</li>
 * </ol>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String DEFAULT_ROLE = "USER";

    /** 用户服务写入 Redis 的会话 Key 前缀（见 RedisSessionServiceImpl） */
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    /** Redis 会话比对失败时前端据此触发清 token + 跳登录页 */
    private static final int CODE_SESSION_INVALID = 40101;

    /** loadStoredToken 的哨兵值：Redis 中确无该会话 */
    private static final String SESSION_ABSENT = "\u0000absent";
    /** loadStoredToken 的哨兵值：Redis 访问异常，本次请求降级放行 */
    private static final String SESSION_UNAVAILABLE = "\u0000unavailable";

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

    /**
     * 仅允许服务间直连调用的内部接口：这些路径没有用户身份语义，
     * 经网关暴露会被任意登录用户用来篡改库存 / 触发补偿。
     */
    private static final List<String> INTERNAL_ONLY_PREFIX_LIST = List.of(
            "/api/product/products/deduct-stock",
            "/api/product/products/seckill/rollback"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Redis 会话校验开关：中间件不可用的极端场景下可临时关闭 */
    @Value("${gateway.session-check.enabled:true}")
    private boolean sessionCheckEnabled;

    private final ReactiveStringRedisTemplate sessionRedisTemplate;
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(ReactiveRedisConnectionFactory redisConnectionFactory,
                            ObjectMapper objectMapper) {
        this.sessionRedisTemplate = new ReactiveStringRedisTemplate(redisConnectionFactory);
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 0. 无论后续走哪条分支，先剥离客户端伪造的身份头
        ServerWebExchange sanitized = stripTrustedHeaders(exchange);
        String path = sanitized.getRequest().getURI().getPath();

        // 1. 内部接口禁止外部访问
        if (isInternalOnly(path)) {
            log.warn("拒绝外部访问内部接口: path={}", path);
            return writeErrorResponse(sanitized, HttpStatus.FORBIDDEN,
                    Result.error(403, "内部接口，禁止外部访问"));
        }

        // 白名单路径直接放行
        if (isWhiteListed(path)) {
            return chain.filter(sanitized);
        }

        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(sanitized.getRequest().getMethod().name())) {
            return chain.filter(sanitized);
        }

        // 2. 提取 Authorization 头
        String authHeader = sanitized.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
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
            String role = claims.get("role", String.class);

            if (userId == null) {
                return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                        Result.error(401, "Token 信息不完整"));
            }

            // 3. Redis 会话校验：登出与单设备互踢依赖此步骤
            if (!sessionCheckEnabled) {
                return forwardWithIdentity(sanitized, chain, userId, username, role);
            }
            return loadStoredToken(userId)
                    .flatMap(stored -> {
                        if (SESSION_UNAVAILABLE.equals(stored) || stored.equals(token)) {
                            return forwardWithIdentity(sanitized, chain, userId, username, role);
                        }
                        if (SESSION_ABSENT.equals(stored)) {
                            log.info("会话不存在或已登出: userId={}", userId);
                            return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                                    Result.error(CODE_SESSION_INVALID, "会话已失效，请重新登录"));
                        }
                        log.warn("Token 与 Redis 会话不一致，疑似被其他设备挤下线: userId={}", userId);
                        return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                                Result.error(CODE_SESSION_INVALID, "账号已在其他设备登录，当前会话已失效"));
                    });

        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "Token 已过期，请重新登录"));
        } catch (JwtException e) {
            log.warn("Token 无效: {}", e.getMessage());
            return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "Token 无效"));
        } catch (Exception e) {
            log.error("鉴权异常: {}", e.getMessage());
            return writeErrorResponse(sanitized, HttpStatus.UNAUTHORIZED,
                    Result.error(401, "认证失败"));
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /** 读取 Redis 中该用户最新会话 Token；Key 不存在与访问异常用哨兵值区分，异常时降级放行 */
    private Mono<String> loadStoredToken(Long userId) {
        return sessionRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId)
                .defaultIfEmpty(SESSION_ABSENT)
                .onErrorResume(e -> {
                    log.error("Redis 会话校验不可用，本次请求降级放行: userId={}, error={}",
                            userId, e.getMessage());
                    return Mono.just(SESSION_UNAVAILABLE);
                });
    }

    /** 注入身份头 + 网关签名后转发下游 */
    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange, GatewayFilterChain chain,
                                           Long userId, String username, String role) {
        String effectiveRole = (role == null || role.isBlank()) ? DEFAULT_ROLE : role;
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = GatewaySignature.sign(jwtSecret,
                GatewaySignature.buildPayload(userId, username, effectiveRole, timestamp));

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .header(HEADER_USER_NAME, username)
                .header(HEADER_USER_ROLE, effectiveRole)
                .header(GatewaySignature.HEADER_GATEWAY_TIMESTAMP, String.valueOf(timestamp))
                .header(GatewaySignature.HEADER_GATEWAY_SIGNATURE, signature)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /** 移除客户端传入的信任凭据头，只允许网关自身注入 */
    private ServerWebExchange stripTrustedHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        List<String> trustedHeaders = List.of(
                HEADER_USER_ID, HEADER_USER_NAME, HEADER_USER_ROLE,
                GatewaySignature.HEADER_GATEWAY_TIMESTAMP, GatewaySignature.HEADER_GATEWAY_SIGNATURE);

        boolean hasTrustedHeader = trustedHeaders.stream()
                .anyMatch(name -> request.getHeaders().containsKey(name));
        if (!hasTrustedHeader) {
            return exchange;
        }
        log.warn("检测到客户端自带身份头，已剥离以防伪造: path={}", request.getURI().getPath());

        ServerHttpRequest stripped = request.mutate()
                .headers(httpHeaders -> trustedHeaders.forEach(httpHeaders::remove))
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private boolean isWhiteListed(String path) {
        if (WHITE_LIST.stream().anyMatch(path::equals)) {
            return true;
        }
        return WHITE_PREFIX_LIST.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalOnly(String path) {
        return INTERNAL_ONLY_PREFIX_LIST.stream().anyMatch(path::startsWith);
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
