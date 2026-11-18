package com.ecommerce.product.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.support.ImageUploadSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘图片存储：OSS 凭证未配置时（本地开发、测试环境）的兜底实现。
 *
 * <p>文件落在 {@code oss.local-dir} 目录下，目录结构与 OSS 对象 Key 一致（product/yyyy/MM/dd/uuid.ext），
 * 由商品服务以静态资源形式对外提供，返回的 URL 为相对路径，经网关白名单读取，无需硬编码主机与端口。
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final OssProperties properties;

    public LocalFileStorageService(OssProperties properties) {
        this.properties = properties;
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        String contentType = ImageUploadSupport.validateAndDetect(file, properties);
        String objectKey = ImageUploadSupport.objectKeyOf(properties, contentType);
        Path target = resolveInsideRoot(objectKey);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("本地图片保存失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(500, "图片保存失败，请稍后重试");
        }

        String url = buildPublicUrl(objectKey);
        log.info("本地图片保存成功: objectKey={}, size={}B, type={}", objectKey, file.getSize(), contentType);
        return new UploadResult(url, objectKey, file.getOriginalFilename(), file.getSize(), contentType);
    }

    @Override
    public void delete(String objectKey) {
        ImageUploadSupport.validateObjectKey(objectKey, properties);
        Path target = resolveInsideRoot(objectKey);
        try {
            if (Files.deleteIfExists(target)) {
                log.info("本地图片删除成功: objectKey={}", objectKey);
            }
        } catch (IOException e) {
            log.error("本地图片删除失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(500, "图片删除失败，请稍后重试");
        }
    }

    /** 本地存储根目录（绝对路径），与静态资源映射使用同一目录 */
    public static Path resolveRoot(OssProperties properties) {
        return Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
    }

    /** 解析对象 Key 对应的磁盘路径，并确保不会越出根目录 */
    private Path resolveInsideRoot(String objectKey) {
        Path root = resolveRoot(properties);
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(400, "非法的对象路径");
        }
        return target;
    }

    private String buildPublicUrl(String objectKey) {
        String prefix = properties.getLocalUrlPrefix();
        String normalized = prefix == null || prefix.isBlank() ? "/api/product/files/local" : prefix.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/" + objectKey;
    }
}
