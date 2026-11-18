package com.ecommerce.product.service.support;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.properties.OssProperties;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 图片上传公共校验与对象 Key 规则。
 *
 * <p>OSS 与本地磁盘两种存储实现共用同一套校验：不信任客户端声明的文件名与 Content-Type，
 * 通过魔数识别真实图片类型后按类型生成扩展名，对象 Key 使用 UUID 避免路径穿越与重名覆盖。
 */
public final class ImageUploadSupport {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MAGIC_LENGTH = 12;

    private ImageUploadSupport() {
    }

    /**
     * 校验文件非空、未超大小上限且内容确为允许的图片，返回检测出的真实 MIME 类型。
     *
     * @return 识别出的图片类型，如 image/png
     */
    public static String validateAndDetect(MultipartFile file, OssProperties properties) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        long maxBytes = properties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, "图片大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(MAGIC_LENGTH);
        } catch (IOException e) {
            throw new BusinessException(400, "读取上传文件失败");
        }

        String contentType = detectImageType(header);
        if (contentType == null || !properties.getAllowedTypes().contains(contentType)) {
            throw new BusinessException(400, "仅支持 JPG / PNG / WEBP / GIF 格式的图片");
        }
        return contentType;
    }

    /** 生成对象 Key：{dir}/yyyy/MM/dd/{uuid}.{ext} */
    public static String objectKeyOf(OssProperties properties, String contentType) {
        return normalizeDir(properties) + "/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extensionOf(contentType);
    }

    /** 校验对象 Key 非空、位于配置目录内且不含路径穿越片段 */
    public static void validateObjectKey(String objectKey, OssProperties properties) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(400, "objectKey 不能为空");
        }
        String dirPrefix = normalizeDir(properties) + "/";
        if (!objectKey.startsWith(dirPrefix) || objectKey.contains("..")) {
            throw new BusinessException(400, "非法的对象路径");
        }
    }

    /** 去掉配置目录首尾多余的斜杠，保证 Key 前缀稳定 */
    public static String normalizeDir(OssProperties properties) {
        String dir = properties.getDir() == null ? "product" : properties.getDir().trim();
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
    }

    /** 按魔数识别真实图片类型，无法识别返回 null */
    private static String detectImageType(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "image/png";
        }
        if (header.length >= 6
                && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return "image/gif";
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
