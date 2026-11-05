package com.example.demo.security;

import com.example.demo.common.Result;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RedisSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：从 Authorization: Bearer &lt;token&gt; 提取并验证令牌，
 * 通过后将用户认证信息注入 SecurityContext。
 * 
 * <p>校验流程：
 * <ol>
 *   <li>验证 JWT 签名和有效期</li>
 *   <li>从 JWT 中提取 userId</li>
 *   <li>查询 Redis 中该用户的最新 Token</li>
 *   <li>比对请求 Token 与 Redis Token 是否一致（不一致则判定为被挤下线）</li>
 * </ol>
 * 
 * <p>Token 缺失时不拦截（交给 Security 的 EntryPoint 统一返回未授权）；
 * Token 存在但无效/过期/被挤下线时对受保护接口直接返回 401 与明确错误信息。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** 业务错误码：账号在其他设备登录，已被迫下线 */
    private static final int CODE_KICKED_OUT = 40101;

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RedisSessionService redisSessionService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   UserRepository userRepository,
                                   RedisSessionService redisSessionService,
                                   ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.redisSessionService = redisSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 未携带 Token：放行给过滤链，受保护接口由 AuthenticationEntryPoint 处理
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims;
        try {
            claims = tokenProvider.parseToken(token);
        } catch (ExpiredJwtException e) {
            writeUnauthorized(response, "Token 已过期，请重新登录");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response, "Token 无效");
            return;
        }

        String username = claims.getSubject();
        
        // 从 JWT 中提取 userId
        Long userId;
        try {
            userId = claims.get(JwtTokenProvider.CLAIM_USER_ID, Long.class);
        } catch (Exception e) {
            log.warn("JWT 中缺少 userId claim，username={}", username);
            writeUnauthorized(response, "Token 信息不完整");
            return;
        }
        
        // Redis 校验：比对请求 Token 与 Redis 中最新 Token 是否一致
        // 不一致则说明该账号已在其他设备登录，当前设备被挤下线
        if (!redisSessionService.validateToken(userId, token)) {
            writeKickedOutResponse(response);
            return;
        }
        
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            writeUnauthorized(response, "用户不存在或已被删除，请重新登录");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** 以项目统一 Result 结构写出 401 响应 */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(401, message));
    }

    /**
     * 写出被挤下线的 401 响应。
     * 使用业务码 40101 区分于普通的 Token 无效/过期，前端可据此显示特定提示。
     */
    private void writeKickedOutResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), 
                Result.error(CODE_KICKED_OUT, "您的账号已在其他设备登录，您已被迫下线"));
    }
}
