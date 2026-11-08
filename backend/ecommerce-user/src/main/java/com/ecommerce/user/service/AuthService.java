package com.ecommerce.user.service;

import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.SendSmsCodeRequest;
import com.ecommerce.user.entity.User;

/**
 * 认证服务：注册 / 登录 / 查询当前用户 / 发送登录注册用短信验证码
 */
public interface AuthService {

    /** 注册新用户，返回创建后的用户（不含密码） */
    User register(RegisterRequest request);

    /** 用户名 + 密码登录，返回 JWT 与用户信息 */
    LoginResponse login(LoginRequest request);

    /** 根据认证标识查询当前用户 */
    User getCurrentUser(String username);

    /**
     * 登录 / 注册页发送短信验证码：先校验图片验证码，通过后按手机号（注册场景）或
     * 用户名查询到的手机号（登录场景）发送 6 位数字验证码。
     */
    void sendSmsCode(SendSmsCodeRequest request);
}
