package com.example.demo.dto;

import lombok.Data;

/**
 * 图片验证码生成接口响应：
 * - captchaKey：客户端提交登录 / 注册时原样回传，用于服务端定位本次生成的验证码
 * - captchaImage：Base64 PNG Data URL（data:image/png;base64,...），可直接用于 img 标签展示
 */
@Data
public class CaptchaResponse {

    private String captchaKey;

    private String captchaImage;

    public CaptchaResponse(String captchaKey, String captchaImage) {
        this.captchaKey = captchaKey;
        this.captchaImage = captchaImage;
    }
}
