package com.example.demo.service.impl;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.service.AuthService;
import com.example.demo.service.CaptchaService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：
 * - 注册 / 登录都必须先通过图片验证码校验（{@link CaptchaService#validate}），
 *   校验失败抛出统一的业务异常，不再执行后续业务逻辑
 * - 密码使用 BCryptPasswordEncoder 加密存储（密文随用户记录持久化到 MySQL，
 *   实体上 @JsonIgnore 保证密文不会出现在任何接口响应里）
 * - 认证标识（username）唯一性由 userRepository.existsByUsername 保证
 * - 注册产生的用户，审计字段 createdBy / lastModifiedBy 由 UserRepository 自动记为 "system"
 *   （注册时尚无登录上下文，符合"未登录或系统操作默认记录 system"的约定）
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final CaptchaService captchaService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.captchaService = captchaService;
    }

    @Override
    public User register(RegisterRequest request) {
        // 执行业务逻辑前必须先校验验证码（正确性 / 是否过期 / 是否已被使用）
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue());

        // 必填与格式校验已由 @Valid 完成，这里处理业务级校验
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(409, "认证标识已存在: " + request.getUsername());
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("确认密码与密码不一致");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAge(request.getAge());
        return userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 执行业务逻辑前必须先校验验证码（正确性 / 是否过期 / 是否已被使用）
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误：认证标识不存在"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误：密码不正确");
        }
        String token = tokenProvider.generateToken(user.getUsername(), user.getId());
        return new LoginResponse(token, tokenProvider.getExpiresInSeconds(), user);
    }

    @Override
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }
}
