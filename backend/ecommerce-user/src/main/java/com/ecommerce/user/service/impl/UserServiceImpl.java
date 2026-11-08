package com.ecommerce.user.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

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
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(404, "用户不存在: id=" + id);
        }
        userRepository.deleteById(id);
    }
}
