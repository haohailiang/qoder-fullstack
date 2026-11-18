package com.ecommerce.product.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.ecommerce.common.BusinessException;
import com.ecommerce.product.config.OssClientProvider;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.impl.OssFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OSS 图片存储实现单元测试：类型/大小校验、对象 Key 规则、URL 拼接与未配置降级。
 */
@ExtendWith(MockitoExtension.class)
class OssFileStorageServiceTest {

    private static final byte[] JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock
    private OssClientProvider clientProvider;

    @Mock
    private OSS oss;

    private OssProperties properties;
    private OssFileStorageService service;

    @BeforeEach
    void setUp() {
        properties = new OssProperties();
        properties.setBucket("demo-bucket");
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setDir("product");
        properties.setMaxSizeMb(1);
        properties.setAccessKeyId("test-ak");
        properties.setAccessKeySecret("test-sk");
        service = new OssFileStorageService(properties, clientProvider);
    }

    @Test
    @DisplayName("上传 JPEG：返回按 bucket/endpoint 拼接的公网 URL")
    void shouldUploadJpegAndBuildPublicUrl() {
        when(clientProvider.getClient()).thenReturn(oss);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG_BYTES);

        FileStorageService.UploadResult result = service.upload(file);

        assertTrue(result.getObjectKey().startsWith("product/"));
        assertTrue(result.getObjectKey().endsWith(".jpg"));
        assertEquals("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/" + result.getObjectKey(), result.getUrl());
        assertEquals("image/jpeg", result.getContentType());
        verify(oss).putObject(eq("demo-bucket"), startsWith("product/"), any(InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    @DisplayName("配置了 CDN 前缀时优先使用自定义域名")
    void shouldUsePublicUrlPrefix() {
        properties.setPublicUrlPrefix("https://cdn.example.com/images/");
        when(clientProvider.getClient()).thenReturn(oss);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);

        FileStorageService.UploadResult result = service.upload(file);

        assertTrue(result.getUrl().startsWith("https://cdn.example.com/images/product/"));
        assertTrue(result.getObjectKey().endsWith(".png"));
    }

    @Test
    @DisplayName("非图片内容（魔数不匹配）被拒绝且不访问 OSS")
    void shouldRejectNonImageContent() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "image/png", "hello".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(400, ex.getCode());
        verifyNoInteractions(clientProvider);
    }

    @Test
    @DisplayName("超过大小上限被拒绝且不访问 OSS")
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
        verifyNoInteractions(clientProvider);
    }

    @Test
    @DisplayName("删除：非法对象路径被拒绝且不访问 OSS")
    void shouldRejectIllegalObjectKey() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("../secret.png"));

        assertEquals(400, ex.getCode());
        verifyNoInteractions(clientProvider);
    }

    @Test
    @DisplayName("删除：合法对象 Key 调用 OSS deleteObject")
    void shouldDeleteValidObjectKey() {
        when(clientProvider.getClient()).thenReturn(oss);

        service.delete("product/2026/11/18/a.png");

        verify(oss).deleteObject("demo-bucket", "product/2026/11/18/a.png");
    }

    @Test
    @DisplayName("AK/SK 未配置时返回 503 业务错误")
    void shouldReturn503WhenOssNotConfigured() {
        OssFileStorageService unconfigured = new OssFileStorageService(
                new OssProperties(), new OssClientProvider(new OssProperties()));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);

        BusinessException ex = assertThrows(BusinessException.class, () -> unconfigured.upload(file));

        assertEquals(503, ex.getCode());
    }
}
