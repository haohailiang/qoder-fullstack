package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口：匿名可访问（SecurityConfig 已放行 /health），
 * 实际访问路径为 GET /api/health。
 */
@RestController
@Tag(name = "健康检查", description = "服务健康状态检查接口")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查服务是否正常运行，返回 UP 状态")
    public Result<String> health() {
        return Result.success("UP");
    }
}
