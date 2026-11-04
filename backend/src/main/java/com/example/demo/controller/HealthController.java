package com.example.demo.controller;

import com.example.demo.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口：匿名可访问（SecurityConfig 已放行 /health），
 * 实际访问路径为 GET /api/health。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("UP");
    }
}
