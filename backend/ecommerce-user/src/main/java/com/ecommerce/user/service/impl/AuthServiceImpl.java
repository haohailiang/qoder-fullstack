package com.ecommerce.user.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.SendSmsCodeRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtTokenProvider;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.CaptchaService;
import com.ecommerce.user.service.RedisSessionService;
import com.ecommerce.user.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   （注册时尚无登录上下文，符合“未登录或系统操作默认记录 system”的约定）
 * - 登录成功后将 JWT Token 写入 Redis，实现单设备登录（互踢/挤下线）机制
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final CaptchaService captchaService;
    private final SmsService smsService;
    private final RedisSessionService redisSessionService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           CaptchaService captchaService,
                           SmsService smsService,
                           RedisSessionService redisSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.captchaService = captchaService;
        this.smsService = smsService;
        this.redisSessionService = redisSessionService;
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
        
        // 第三步：尝试获取分布式锁，防止同一账号并发登录导致会话状态错乱
        if (!redisSessionService.tryAcquireLoginLock(user.getId())) {
            throw new BusinessException(429, "登录请求过于频繁，请稍后重试");
        }
        
        try {
            // 第四步：生成新 JWT Token
            String token = tokenProvider.generateToken(user.getUsername(), user.getId());
            
            // 第五步：将新 Token 写入 Redis（覆盖旧 Token，使旧设备自动失效）
            redisSessionService.saveToken(user.getId(), token, tokenProvider.getExpiresInSeconds());
            
            log.info("用户 {} 登录成功，新 Token 已写入 Redis", user.getUsername());
            return new LoginResponse(token, tokenProvider.getExpiresInSeconds(), user);
        } finally {
            // 第六步：释放分布式锁
            redisSessionService.releaseLoginLock(user.getId());
        }
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
