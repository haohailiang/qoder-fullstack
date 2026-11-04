package com.example.demo.config;

import com.example.demo.config.properties.SmsProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 短信调用专用 RestTemplate：连接 / 读取超时时间从 sms.timeout-ms 读取，
 * 避免云片网接口无响应时长时间阻塞业务线程。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate smsRestTemplate(RestTemplateBuilder builder, SmsProperties properties) {
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
