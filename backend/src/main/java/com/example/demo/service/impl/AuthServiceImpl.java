package com.example.demo.service.impl;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：
 * - 密码使用 BCryptPasswordEncoder 加密存储（密文与用户对象一起保存在内存仓储的 User 实例中，
 *   实体上 @JsonIgnore 保证密文不会出现在任何接口响应里）
 * - 认证标识（username）在内存仓储中通过 existsByUsername 保证唯一
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public User register(RegisterRequest request) {
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
        user.setAge(request.getAge());
        return userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
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
