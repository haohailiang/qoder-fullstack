package com.ecommerce.order.config;

import com.ecommerce.common.security.GatewaySignature;
import com.ecommerce.common.security.TrustedAuthenticationFilter;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Feign 调用身份透传配置。
 *
 * <p>订单服务调用商品服务（扣减库存、秒杀回滚）时，把当前请求携带的 JWT、
 * 网关注入的身份头与网关签名头原样透传，使下游服务仍能完成身份识别与签名校验
 * （透传签名而非重新签名，是为了 {@code security.trusted-header.verify-signature=true}
 * 时内部链路依然可用；签名时间戳受 max-age-seconds 约束，跨服务调用耗时需远小于该窗口）。
 *
 * <p>无请求上下文的线程（RabbitMQ 消费线程、@Async 线程）直接跳过：此时下游按内部匿名调用处理，
 * 相关的内部接口本身就不依赖用户身份。
 */
@Configuration
public class FeignIdentityConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignIdentityConfig.class);

    /** 需要透传的身份相关请求头 */
    private static final List<String> RELAY_HEADERS = List.of(
            "Authorization",
            TrustedAuthenticationFilter.HEADER_USER_ID,
            TrustedAuthenticationFilter.HEADER_USER_NAME,
            TrustedAuthenticationFilter.HEADER_USER_ROLE,
            GatewaySignature.HEADER_GATEWAY_TIMESTAMP,
            GatewaySignature.HEADER_GATEWAY_SIGNATURE);

    @Bean
    public RequestInterceptor identityRelayInterceptor() {
        return template -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
                log.debug("当前线程无请求上下文（MQ/异步线程），跳过身份透传");
                return;
            }
            HttpServletRequest request = servletAttributes.getRequest();
            for (String header : RELAY_HEADERS) {
                String value = request.getHeader(header);
                if (value != null && !value.isBlank()) {
                    template.header(header, value);
                }
            }
        };
    }
}
