package com.ecommerce.user.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    /** 合法角色集合：白名单避免请求体里塞入任意字符串造成脏角色 */
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在: id=" + id));
    }

    @Override
    public User createUser(User user) {
        // 新建时忽略客户端传入的 id，由仓储层生成
        user.setId(null);
        user.setRole(normalizeRole(user.getRole()));
        return userRepository.save(user);
    }

    @Override
    public User updateUser(Long id, User user) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在: id=" + id));
        user.setId(id);
        // 管理页编辑表单不包含 username/password，缺失时保留原值，避免登录后被清空
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(existing.getUsername());
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(existing.getPassword());
        }
        // 角色不通过普通编辑变更：强制保留原值（UserMapper.updateById 也不更新 role）
        user.setRole(existing.getRole());
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(404, "用户不存在: id=" + id);
        }
        userRepository.deleteById(id);
    }

    /** 角色归一：空值或非白名单值一律降为 USER */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return DEFAULT_ROLE;
        }
        String upper = role.trim().toUpperCase();
        return ALLOWED_ROLES.contains(upper) ? upper : DEFAULT_ROLE;
    }
}
