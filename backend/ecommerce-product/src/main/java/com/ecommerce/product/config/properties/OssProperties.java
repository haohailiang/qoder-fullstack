package com.ecommerce.product.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 OSS 配置。
 *
 * <p>AK/SK 只通过环境变量注入，禁止写入仓库；未配置时应用照常启动，
 * 仅上传/删除接口返回“OSS 未配置”的业务错误，避免本地开发与测试环境强依赖云资源。
 */
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** 是否启用 OSS 上传能力 */
    private boolean enabled = true;

    /** OSS 访问域名，如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint = "oss-cn-hangzhou.aliyuncs.com";

    /** Bucket 名称 */
    private String bucket = "";

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /** 对象 Key 前缀，最终 Key 形如 product/2026/11/18/uuid.png */
    private String dir = "product";

    /** 公有读访问前缀（CDN 或自定义域名），留空时按 bucket + endpoint 拼接 */
    private String publicUrlPrefix = "";

    /** 本地兜底存储根目录（AK/SK 未配置时生效），相对路径以服务工作目录为基准 */
    private String localDir = "uploads";

    /** 本地兜底存储的对外访问前缀：经网关读取商品服务静态资源，与前端 /api 前缀保持一致 */
    private String localUrlPrefix = "/api/product/files/local";

    /** 单文件大小上限（MB） */
    private long maxSizeMb = 5;

    /** 允许的图片 MIME 类型 */
    private List<String> allowedTypes = new ArrayList<>(
            List.of("image/jpeg", "image/png", "image/webp", "image/gif"));

    /** AK 是否已配置，用于判断上传能力是否可用 */
    public boolean isConfigured() {
        return enabled && !accessKeyId.isBlank() && !accessKeySecret.isBlank() && !bucket.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getPublicUrlPrefix() {
        return publicUrlPrefix;
    }

    public void setPublicUrlPrefix(String publicUrlPrefix) {
        this.publicUrlPrefix = publicUrlPrefix;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public String getLocalUrlPrefix() {
        return localUrlPrefix;
    }

    public void setLocalUrlPrefix(String localUrlPrefix) {
        this.localUrlPrefix = localUrlPrefix;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public List<String> getAllowedTypes() {
        return allowedTypes;
    }

    public void setAllowedTypes(List<String> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }
}
