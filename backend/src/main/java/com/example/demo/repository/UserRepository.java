package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户仓储：基于 ConcurrentHashMap 的内存实现，开箱即用。
 * 后续如需持久化，可替换为 JPA / MyBatis 实现并保持方法签名不变。
 * 密码字段（password）与用户对象一起存在同一个 User 实例中，由调用方写入 BCrypt 密文；
 * 实体上的 @JsonIgnore 保证密文不会出现在接口响应里。
 */
@Repository
public class UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public UserRepository() {
        // 预置两条带账号密码的示例数据，便于启动后直接联调（初始密码均为 123456）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        save(createUser("zhangsan", encoder.encode("123456"), "张三", "zhangsan@example.com", 25));
        save(createUser("lisi", encoder.encode("123456"), "李四", "lisi@example.com", 30));
    }

    private User createUser(String username, String password, String name, String email, Integer age) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setName(name);
        user.setEmail(email);
        user.setAge(age);
        return user;
    }

    public List<User> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst();
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(idGenerator.incrementAndGet());
        }
        store.put(user.getId(), user);
        return user;
    }

    public void deleteById(Long id) {
        store.remove(id);
    }
}
