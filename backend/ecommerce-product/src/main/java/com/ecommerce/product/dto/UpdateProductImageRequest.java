package com.ecommerce.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商品图片回写请求：仅更新 image_url 字段，避免整实体覆盖导致其他字段被置空。
 *
 * <p>传空字符串表示清除商品图片，此时仅清空数据库字段，不删除 OSS 对象。
 *
 * <p>imageUrl 允许两种形态：OSS 模式返回的 http(s) 绝对地址；本地兜底存储返回的
 * 站内相对路径（如 /api/product/files/local/product/2026/11/18/xxx.png）。
 */
public class UpdateProductImageRequest {

    @Size(max = 500, message = "图片 URL 长度不能超过 500 个字符")
    // 以 / 开头但排除 //evil.com 这类协议相对地址，避免被当作外链跳转
    @Pattern(regexp = "^$|^https?://.+|^/(?!/).+", message = "图片 URL 必须为 http:// 或 https:// 地址，或以 / 开头的站内路径")
    private String imageUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
