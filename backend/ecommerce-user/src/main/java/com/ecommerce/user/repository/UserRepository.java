package com.ecommerce.user.repository;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储：基于 MyBatis（{@link UserMapper}，SQL 见 mapper/UserMapper.xml）访问 MySQL。
 * 对外方法签名与早期内存实现保持一致，AuthServiceImpl / UserServiceImpl / JwtAuthenticationFilter
 * 等调用方无需改动。
 * <p>
 * 审计字段（createdBy / createdDate / lastModifiedBy / lastModifiedDate）统一在 {@link #save(User)}
 * 中自动填充：新增时写入 createdBy / createdDate（之后不再变化），每次新增/更新都会刷新
 * lastModifiedBy / lastModifiedDate；操作人取当前登录用户标识，未登录或系统内部操作（如注册、
 * 启动数据初始化）默认记为 "system"，时间取服务端当前时间。
 * 密码字段（password）与用户对象一起保存在同一行记录中，由调用方写入 BCrypt 密文；
 * 实体上的 @JsonIgnore 保证密文不会出现在接口响应里。
 */
@Repository
public class UserRepository {

    private static final String SYSTEM_OPERATOR = "system";

    private final UserMapper userMapper;

    public UserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<User> findAll() {
        return userMapper.selectAll();
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectByUsername(username));
    }

    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    public boolean existsById(Long id) {
        return userMapper.countById(id) > 0;
    }

    public User save(User user) {
        String operator = currentUserOrSystem();
        LocalDateTime now = LocalDateTime.now();
        if (user.getId() == null) {
            // 新增：createdBy / createdDate 与 lastModifiedBy / lastModifiedDate 均在此处首次写入
            user.setCreatedBy(operator);
            user.setCreatedDate(now);
            user.setLastModifiedBy(operator);
            user.setLastModifiedDate(now);
            userMapper.insert(user);
        } else {
            // 更新：createdBy / createdDate 保持创建时的值不变（见 UserMapper.xml 中 updateById 未涉及该两列）
            user.setLastModifiedBy(operator);
            user.setLastModifiedDate(now);
            userMapper.updateById(user);
        }
        return user;
    }

    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    /** 更新用户主题偏好（dark / light） */
    public void updateThemePreference(Long id, String themePreference) {
        userMapper.updateThemePreference(id, themePreference);
    }

    /**
     * 读取当前登录用户标识（JWT 认证后写入 SecurityContext 的 username）；
     * 未认证、匿名访问或系统内部操作时返回 "system"。
     */
    private String currentUserOrSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_OPERATOR;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return SYSTEM_OPERATOR;
        }
        return name;
    }
}
