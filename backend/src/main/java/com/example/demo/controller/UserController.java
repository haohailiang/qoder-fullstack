package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
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

/**
 * 用户资源接口。
 * 实际访问路径为 /api/users（/api 来自 server.servlet.context-path 配置）。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 创建一个 /hello 的 GET 请求处理方法，返回 "Hello, World!"
    @GetMapping("/hello1")
    public String hello() {
        return "Hello, World!";
    }

    /** 查询用户列表 */
    @GetMapping
    public Result<List<User>> list() {
        return Result.success(userService.listUsers());
    }

    /** 根据 id 查询用户 */
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        return Result.success(userService.getUser(id));
    }

    /** 新增用户 */
    @PostMapping
    public Result<User> create(@Valid @RequestBody User user) {
        return Result.success(userService.createUser(user));
    }

    /** 更新用户 */
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody User user) {
        return Result.success(userService.updateUser(id, user));
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
}
