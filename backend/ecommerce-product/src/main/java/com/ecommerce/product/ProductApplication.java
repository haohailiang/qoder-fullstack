package com.ecommerce.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.ecommerce.common.GlobalExceptionHandler;
import com.ecommerce.common.config.SeckillRabbitConfig;
import com.ecommerce.common.config.SecurityConfig;
import com.ecommerce.common.security.TrustedAuthenticationFilter;

@SpringBootApplication(scanBasePackages = "com.ecommerce.product")
@MapperScan("com.ecommerce.product.repository.mapper")
@Import({GlobalExceptionHandler.class, SeckillRabbitConfig.class, SecurityConfig.class, TrustedAuthenticationFilter.class})
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
