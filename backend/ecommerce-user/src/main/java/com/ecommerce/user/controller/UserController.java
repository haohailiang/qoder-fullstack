package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.TrustedAuthenticationFilter;
import com.ecommerce.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户资源接口。
 * 实际访问路径为 /api/users（/api 来自 server.servlet.context-path 配置）。
 */
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户 CRUD 操作接口")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // 创建一个 /hello 的 GET 请求处理方法，返回 "Hello, World!"
    @GetMapping("/hello1")
    @Operation(summary = "测试接口", description = "返回 Hello World 字符串，用于测试服务是否正常")
    public String hello() {
        return "Hello, World!";
    }

    /** 查询用户列表 */
    @GetMapping
    @Operation(summary = "查询用户列表", description = "查询所有用户信息")
    public Result<List<User>> list() {
        return Result.success(userService.listUsers());
    }

    /** 根据 id 查询用户 */
    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情", description = "根据用户 ID 查询单个用户信息")
    public Result<User> get(@Parameter(description = "用户 ID") @PathVariable Long id) {
        return Result.success(userService.getUser(id));
    }

    /** 新增用户 */
    @PostMapping
    @Operation(summary = "新增用户", description = "创建新用户，需要提供用户名、姓名、邮箱、手机号等信息")
    public Result<User> create(@Valid @RequestBody User user) {
        return Result.success(userService.createUser(user));
    }

    /** 更新用户 */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据用户 ID 更新用户信息")
    public Result<User> update(@Parameter(description = "用户 ID") @PathVariable Long id, @Valid @RequestBody User user) {
        return Result.success(userService.updateUser(id, user));
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据用户 ID 删除指定用户")
    public Result<Void> delete(@Parameter(description = "用户 ID") @PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    /** 获取当前登录用户的主题偏好 */
    @GetMapping("/theme")
    @Operation(summary = "获取主题偏好", description = "获取当前登录用户的主题偏好设置（dark/light）")
    public Result<Map<String, String>> getTheme(HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.ecommerce.common.BusinessException(404, "用户不存在"));
        String theme = user.getThemePreference() != null ? user.getThemePreference() : "dark";
        return Result.success(Map.of("theme", theme));
    }

    /** 更新当前登录用户的主题偏好 */
    @PutMapping("/theme")
    @Operation(summary = "更新主题偏好", description = "更新当前登录用户的主题偏好设置（dark/light）")
    public Result<Void> updateTheme(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        String theme = body.get("theme");
        if (theme == null || (!theme.equals("dark") && !theme.equals("light"))) {
            throw new com.ecommerce.common.BusinessException(400, "主题值无效，仅支持 dark 或 light");
        }
        userRepository.updateThemePreference(userId, theme);
        return Result.success();
    }

    private Long getUserIdFromHeader(HttpServletRequest request) {
        String userIdStr = request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ID);
        if (userIdStr == null) {
            throw new com.ecommerce.common.BusinessException(401, "未授权访问");
        }
        return Long.parseLong(userIdStr);
    }
}
