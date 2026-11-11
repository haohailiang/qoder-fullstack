package com.ecommerce.user.config;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化：应用启动完成后（此时无登录上下文，审计字段 createdBy / lastModifiedBy
 * 会由 {@link UserRepository} 统一记为 "system"），若 MySQL users 表中不存在预置演示账号，
 * 则插入两条示例数据，保证开箱即可联调注册 / 登录 / 用户管理接口，
 * 也保证 AuthControllerExceptionTest / SecurityExceptionTest 等集成测试依赖的
 * 预置账号（zhangsan / lisi）在切换到 MySQL 持久化后依然可用。
 * 逻辑幂等：重复启动不会产生脏数据。
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** 演示账号统一初始密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(buildUser("admin", "系统管理员", "admin@ecommerce.com", "13800000000", 35, "ADMIN"));
            log.info("已初始化管理员账号: admin（role=ADMIN）");
        }
        if (userRepository.findByUsername("zhangsan").isEmpty()) {
            userRepository.save(buildUser("zhangsan", "张三", "zhangsan@example.com", "13800000001", 25, "USER"));
        }
        if (userRepository.findByUsername("lisi").isEmpty()) {
            userRepository.save(buildUser("lisi", "李四", "lisi@example.com", "13800000002", 30, "USER"));
        }
        log.info("演示数据初始化检查完成（已存在则跳过插入）");
    }

    private User buildUser(String username, String name, String email, String phone, int age, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setAge(age);
        user.setRole(role);
        return user;
    }
}
