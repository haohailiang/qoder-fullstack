package com.ecommerce.product.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件存储抽象：配置 OSS 凭证时写入阿里云 OSS，未配置时落本地磁盘（见 FileStorageServiceRouter）。
 */
public interface FileStorageService {

    /**
     * 上传图片并返回可公开访问的 URL。
     *
     * @param file 前端上传的图片
     * @return 上传结果（URL、对象 Key 等）
     */
    UploadResult upload(MultipartFile file);

    /**
     * 删除指定对象，objectKey 必须位于配置的目录前缀内。
     *
     * @param objectKey 上传时返回的对象 Key
     */
    void delete(String objectKey);

    /** 上传结果 */
    class UploadResult {

        private String url;
        private String objectKey;
        private String originalName;
        private long size;
        private String contentType;

        public UploadResult() {
        }

        public UploadResult(String url, String objectKey, String originalName, long size, String contentType) {
            this.url = url;
            this.objectKey = objectKey;
            this.originalName = originalName;
            this.size = size;
            this.contentType = contentType;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public String getOriginalName() {
            return originalName;
        }

        public void setOriginalName(String originalName) {
            this.originalName = originalName;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }
}
