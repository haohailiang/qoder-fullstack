package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;

/**
 * 认证服务：注册 / 登录 / 查询当前用户
 */
public interface AuthService {

    /** 注册新用户，返回创建后的用户（不含密码） */
    User register(RegisterRequest request);

    /** 用户名 + 密码登录，返回 JWT 与用户信息 */
    LoginResponse login(LoginRequest request);

    /** 根据认证标识查询当前用户 */
    User getCurrentUser(String username);
}
