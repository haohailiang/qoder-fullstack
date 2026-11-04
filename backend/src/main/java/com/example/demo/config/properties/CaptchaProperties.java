package com.example.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片验证码配置，对应 application.yml 中的 captcha.* 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /** 是否启用验证码生成与校验；false 时注册/登录不校验验证码（便于本地调试或自动化测试关闭） */
    private boolean enabled = true;

    /** 验证码有效期（秒），过期后必须重新获取 */
    private int expireSeconds = 120;

    /** 验证码图片宽度（像素） */
    private int width = 130;

    /** 验证码图片高度（像素） */
    private int height = 44;

    /** 验证码字符个数 */
    private int codeLength = 4;

    /** 干扰线数量 */
    private int lineCount = 15;
}
