package com.ecommerce.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.annotation.Import;
import com.ecommerce.common.GlobalExceptionHandler;

@SpringBootApplication(scanBasePackages = "com.ecommerce.product",
        exclude = {
                ElasticsearchDataAutoConfiguration.class,
                ElasticsearchRestClientAutoConfiguration.class
        })
@MapperScan("com.ecommerce.product.repository.mapper")
@Import(GlobalExceptionHandler.class)
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
