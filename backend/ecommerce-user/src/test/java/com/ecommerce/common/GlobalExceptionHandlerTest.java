package com.ecommerce.common;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试：
 * 直接构造各类异常，验证每个 handler 方法是否映射为正确的统一 Result 结构（code / message）。
 * 覆盖业务异常、参数校验、认证、授权、JWT 过期/无效、兜底异常等全部分支。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常：保留自定义 code 与 message")
    void handleBusinessException() {
        Result<Void> result = handler.handleBusinessException(new BusinessException(409, "认证标识已存在: zhangsan"));

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).isEqualTo("认证标识已存在: zhangsan");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("业务异常：默认 code 为 400")
    void handleBusinessExceptionDefaultCode() {
        Result<Void> result = handler.handleBusinessException(new BusinessException("确认密码与密码不一致"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("确认密码与密码不一致");
    }

    @Test
    @DisplayName("业务异常：短信发送频控 code=429")
    void handleBusinessExceptionRateLimited() {
        Result<Void> result = handler.handleBusinessException(new BusinessException(429, "验证码发送过于频繁，请稍后再试"));

        assertThat(result.getCode()).isEqualTo(429);
        assertThat(result.getMessage()).isEqualTo("验证码发送过于频繁，请稍后再试");
    }

    @Test
    @DisplayName("业务异常：短信发送失败 code=502")
    void handleBusinessExceptionSmsSendFailed() {
        Result<Void> result = handler.handleBusinessException(new BusinessException(502, "短信发送失败，请稍后重试"));

        assertThat(result.getCode()).isEqualTo(502);
        assertThat(result.getMessage()).isEqualTo("短信发送失败，请稍后重试");
    }

    @Test
    @DisplayName("参数校验异常：返回第一个字段错误信息，code=400")
    void handleValidationException() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "username", "认证标识（用户名）不能为空"));

        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Result<Void> result = handler.handleValidationException(exception);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("认证标识（用户名）不能为空");
    }

    @Test
    @DisplayName("参数校验异常：无字段错误时使用兜底文案")
    void handleValidationExceptionEmpty() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Result<Void> result = handler.handleValidationException(exception);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数校验失败");
    }

    @Test
    @DisplayName("认证异常：code=401")
    void handleAuthenticationException() {
        Result<Void> result = handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("用户名或密码错误");
    }

    @Test
    @DisplayName("授权异常：code=403")
    void handleAccessDeniedException() {
        Result<Void> result = handler.handleAccessDeniedException(new AccessDeniedException("forbidden"));

        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMessage()).isEqualTo("未授权访问：权限不足");
    }

    @Test
    @DisplayName("Token 过期：code=401")
    void handleExpiredJwtException() {
        ExpiredJwtException exception = mock(ExpiredJwtException.class);
        when(exception.getMessage()).thenReturn("JWT expired");

        Result<Void> result = handler.handleExpiredJwtException(exception);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("Token 已过期，请重新登录");
    }

    @Test
    @DisplayName("Token 无效：code=401")
    void handleJwtException() {
        Result<Void> result = handler.handleJwtException(new JwtException("Invalid signature"));

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("Token 无效");
    }

    @Test
    @DisplayName("兜底异常：code=500 且携带原始异常信息")
    void handleException() {
        Result<Void> result = handler.handleException(new IllegalStateException("boom"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器内部错误: boom");
    }
}
