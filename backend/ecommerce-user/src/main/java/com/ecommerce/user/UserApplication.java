package com.ecommerce.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.ecommerce.common.GlobalExceptionHandler;
import com.ecommerce.common.config.SecurityConfig;
import com.ecommerce.common.security.TrustedAuthenticationFilter;

/**
 * 用户服务启动类
 */
@SpringBootApplication
@MapperScan("com.ecommerce.user.repository.mapper")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TrustedAuthenticationFilter.class})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
