package com.ecommerce.product.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.impl.LocalFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地磁盘图片存储单元测试：落盘路径、URL 规则、类型/大小校验与删除幂等。
 */
@DisplayName("本地磁盘图片存储")
class LocalFileStorageServiceTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @TempDir
    Path tempDir;

    private OssProperties properties;
    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        properties = new OssProperties();
        properties.setDir("product");
        properties.setMaxSizeMb(1);
        properties.setLocalDir(tempDir.toString());
        service = new LocalFileStorageService(properties);
    }

    @Test
    @DisplayName("上传 PNG：写入本地目录并返回相对访问 URL")
    void shouldStoreFileAndReturnRelativeUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG_BYTES);

        FileStorageService.UploadResult result = service.upload(file);

        assertTrue(result.getObjectKey().startsWith("product/"));
        assertTrue(result.getObjectKey().endsWith(".png"));
        assertEquals("/api/product/files/local/" + result.getObjectKey(), result.getUrl());
        assertEquals("image/png", result.getContentType());
        assertEquals(PNG_BYTES.length, result.getSize());
        assertTrue(Files.exists(tempDir.resolve(result.getObjectKey())));
    }

    @Test
    @DisplayName("自定义访问前缀：首尾斜杠被规范化")
    void shouldNormalizeCustomUrlPrefix() {
        properties.setLocalUrlPrefix("/static/images/");
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG_BYTES);

        FileStorageService.UploadResult result = service.upload(file);

        assertEquals("/static/images/" + result.getObjectKey(), result.getUrl());
    }

    @Test
    @DisplayName("非图片内容（魔数不匹配）被拒绝且不落盘")
    void shouldRejectNonImageContent() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "image/png", "hello".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(400, ex.getCode());
        assertFalse(Files.exists(tempDir.resolve("product")));
    }

    @Test
    @DisplayName("超过大小上限被拒绝")
    void shouldRejectOversizedFile() {
        byte[] bytes = new byte[1024 * 1024 + 1];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("1MB"));
    }

    @Test
    @DisplayName("删除：合法对象 Key 删除磁盘文件")
    void shouldDeleteStoredFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG_BYTES);
        FileStorageService.UploadResult result = service.upload(file);

        service.delete(result.getObjectKey());

        assertFalse(Files.exists(tempDir.resolve(result.getObjectKey())));
    }

    @Test
    @DisplayName("删除：对象不存在时幂等无异常")
    void shouldIgnoreMissingFileOnDelete() {
        service.delete("product/2026/11/18/absent.png");
    }

    @Test
    @DisplayName("删除：非法对象路径被拒绝")
    void shouldRejectIllegalObjectKey() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("../secret.png"));

        assertEquals(400, ex.getCode());
    }
}
