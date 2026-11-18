package com.ecommerce.common;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：所有异常统一包装为 Result 结构返回。
 * 各微服务通过 @Import 引入本类即可复用统一的异常处理逻辑。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /**
     * 上传文件超过 multipart 限制。
     *
     * <p>文件超限在进入业务层前即被容器拒绝，若不单独处理会落到兜底分支返回 500，
     * 这里统一转成 400 并带上服务端限制，便于前端提示用户压缩图片。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        long maxBytes = e.getMaxUploadSize();
        if (maxBytes <= 0) {
            return Result.error(400, "上传文件过大，请压缩后重试");
        }
        long maxMb = Math.max(1, maxBytes / (1024 * 1024));
        return Result.error(400, "上传文件过大，单文件不能超过 " + maxMb + "MB");
    }

    /** 认证异常 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证异常: {}", e.getMessage());
        return Result.error(401, "用户名或密码错误");
    }

    /** 授权异常 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("授权异常: {}", e.getMessage());
        return Result.error(403, "未授权访问：权限不足");
    }

    /** Token 过期 */
    @ExceptionHandler(ExpiredJwtException.class)
    public Result<Void> handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("Token 已过期: {}", e.getMessage());
        return Result.error(401, "Token 已过期，请重新登录");
    }

    /** Token 无效 */
    @ExceptionHandler(JwtException.class)
    public Result<Void> handleJwtException(JwtException e) {
        log.warn("Token 无效: {}", e.getMessage());
        return Result.error(401, "Token 无效");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误: " + e.getMessage());
    }
}
