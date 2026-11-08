package com.ecommerce.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import com.ecommerce.common.GlobalExceptionHandler;
import com.ecommerce.common.config.SeckillRabbitConfig;
import com.ecommerce.common.config.SecurityConfig;
import com.ecommerce.common.security.TrustedAuthenticationFilter;

@SpringBootApplication(scanBasePackages = "com.ecommerce.order")
@MapperScan("com.ecommerce.order.repository.mapper")
@EnableFeignClients(basePackages = "com.ecommerce.order.client")
@Import({GlobalExceptionHandler.class, SeckillRabbitConfig.class, SecurityConfig.class, TrustedAuthenticationFilter.class})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
