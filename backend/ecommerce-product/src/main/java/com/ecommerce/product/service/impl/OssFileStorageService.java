package com.ecommerce.product.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.OssClientProvider;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.support.ImageUploadSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 基于阿里云 OSS 的图片存储实现。
 *
 * <p>安全约束与对象 Key 规则见 {@link ImageUploadSupport}：不信任原始文件名与客户端声明的 Content-Type，
 * 通过魔数校验真实图片类型后按类型生成扩展名，对象 Key 使用 UUID 避免路径穿越与重名覆盖。
 */
public class OssFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssFileStorageService.class);

    private final OssProperties properties;
    private final OssClientProvider clientProvider;

    public OssFileStorageService(OssProperties properties, OssClientProvider clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        String contentType = ImageUploadSupport.validateAndDetect(file, properties);
        String objectKey = ImageUploadSupport.objectKeyOf(properties, contentType);
        OSS client = clientProvider.getClient();
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            metadata.setCacheControl("public, max-age=31536000, immutable");
            metadata.setContentDisposition("inline");
            client.putObject(properties.getBucket(), objectKey, in, metadata);
        } catch (Exception e) {
            log.error("OSS 上传失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(500, "图片上传失败，请稍后重试");
        }

        String url = buildPublicUrl(objectKey);
        log.info("OSS 上传成功: objectKey={}, size={}B, type={}", objectKey, file.getSize(), contentType);
        return new UploadResult(url, objectKey, file.getOriginalFilename(), file.getSize(), contentType);
    }

    @Override
    public void delete(String objectKey) {
        ImageUploadSupport.validateObjectKey(objectKey, properties);
        OSS client = clientProvider.getClient();
        try {
            client.deleteObject(properties.getBucket(), objectKey);
            log.info("OSS 删除成功: objectKey={}", objectKey);
        } catch (Exception e) {
            log.error("OSS 删除失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(500, "图片删除失败，请稍后重试");
        }
    }

    /** 优先使用自定义域名/CDN 前缀，否则按 bucket + endpoint 拼接 */
    private String buildPublicUrl(String objectKey) {
        String prefix = properties.getPublicUrlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return stripTrailingSlash(prefix.trim()) + "/" + objectKey;
        }
        String host = properties.getEndpoint().trim()
                .replaceFirst("^https?://", "");
        return "https://" + properties.getBucket() + "." + stripTrailingSlash(host) + "/" + objectKey;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
