package com.example.demo.service.impl;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.SendSmsCodeRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.service.AuthService;
import com.example.demo.service.CaptchaService;
import com.example.demo.service.SmsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：
 * - 注册 / 登录都采用「图片验证码 → 短信验证码」两步串行校验：先调 {@link CaptchaService#validate}，
 *   再调 {@link SmsService#validateCode}，任一环节失败都抛出统一业务异常，不执行后续业务逻辑（不泄露内部密钥）
 * - sendSmsCode：先校验图片验证码，通过后才真正调用短信服务发送（防滥发），
 *   手机号来源：注册场景前端直传 phone，登录场景由 username 查询用户手机号
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
    private final SmsService smsService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           CaptchaService captchaService,
                           SmsService smsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.captchaService = captchaService;
        this.smsService = smsService;
    }

    @Override
    public User register(RegisterRequest request) {
        // 第一步：校验图片验证码（正确性 / 是否过期 / 是否已被使用）
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue());
        // 第二步：校验短信验证码（注册场景短信已发往 request.phone）
        smsService.validateCode(request.getPhone(), request.getSmsCode());

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
        // 第一步：校验图片验证码（正确性 / 是否过期 / 是否已被使用）
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误：认证标识不存在"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误：密码不正确");
        }
        // 第二步：校验短信验证码（登录场景短信已发往该用户的手机号）
        smsService.validateCode(user.getPhone(), request.getSmsCode());
        String token = tokenProvider.generateToken(user.getUsername(), user.getId());
        return new LoginResponse(token, tokenProvider.getExpiresInSeconds(), user);
    }

    @Override
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    @Override
    public void sendSmsCode(SendSmsCodeRequest request) {
        // 图片验证码作为防滥发手段：校验不通过直接抛异常，短信不会发送
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue());

        String phone = resolveTargetPhone(request);
        // 验证码生成/存储与 60 秒频控均在 SmsService 内部完成
        smsService.sendVerifyCode(phone);
    }

    /**
     * 解析发送目标手机号：
     * - 注册场景：前端直接传入 phone；
     * - 登录场景：前端传入 username，后端查询用户手机号（用户不存在或未设手机号报错）。
     */
    private String resolveTargetPhone(SendSmsCodeRequest request) {
        String phone = request.getPhone();
        if (phone != null && !phone.isBlank()) {
            return phone.trim();
        }
        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "请提供手机号或认证标识");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "认证标识不存在: " + username));
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(400, "该用户未设置手机号，无法发送验证码");
        }
        return user.getPhone();
    }
}
