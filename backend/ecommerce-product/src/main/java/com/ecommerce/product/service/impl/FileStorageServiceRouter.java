package com.ecommerce.product.service.impl;

import com.ecommerce.product.config.OssClientProvider;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片存储入口：按 OSS 凭证是否就绪在同一接口下切换实现。
 *
 * <p>配置了 AK/SK 与 bucket 时写入阿里云 OSS；未配置时（本地开发、测试环境）
 * 自动落到本地磁盘，保证上传功能开箱可用，生产路径不受影响。
 */
@Service
public class FileStorageServiceRouter implements FileStorageService {

    private final OssProperties properties;
    private final FileStorageService ossStorage;
    private final FileStorageService localStorage;

    public FileStorageServiceRouter(OssProperties properties, OssClientProvider clientProvider) {
        this.properties = properties;
        this.ossStorage = new OssFileStorageService(properties, clientProvider);
        this.localStorage = new LocalFileStorageService(properties);
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        return delegate().upload(file);
    }

    @Override
    public void delete(String objectKey) {
        delegate().delete(objectKey);
    }

    /** 凭证就绪走 OSS，否则走本地磁盘；切换需重启服务（配置在启动时绑定） */
    private FileStorageService delegate() {
        return properties.isConfigured() ? ossStorage : localStorage;
    }
}
