package com.ecommerce.product.service;

import com.aliyun.oss.OSS;
import com.ecommerce.product.config.OssClientProvider;
import com.ecommerce.product.config.properties.OssProperties;
import com.ecommerce.product.service.impl.FileStorageServiceRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 存储路由测试：凭证就绪走 OSS，缺省时回退本地磁盘。
 */
@DisplayName("图片存储路由")
class FileStorageServiceRouterTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("未配置 AK/SK 时回退本地磁盘且不触碰 OSS")
    void shouldFallbackToLocalStorageWhenOssNotConfigured() {
        OssProperties properties = new OssProperties();
        properties.setLocalDir(tempDir.toString());
        OssClientProvider provider = Mockito.mock(OssClientProvider.class);
        FileStorageServiceRouter router = new FileStorageServiceRouter(properties, provider);

        FileStorageService.UploadResult result = router.upload(
                new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES));

        assertTrue(result.getUrl().startsWith("/api/product/files/local/product/"));
        assertTrue(Files.exists(tempDir.resolve(result.getObjectKey())));
        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("凭证就绪时上传写入 OSS")
    void shouldUseOssWhenConfigured() {
        OssProperties properties = new OssProperties();
        properties.setBucket("demo-bucket");
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setAccessKeyId("test-ak");
        properties.setAccessKeySecret("test-sk");
        properties.setLocalDir(tempDir.toString());

        OSS oss = Mockito.mock(OSS.class);
        OssClientProvider provider = Mockito.mock(OssClientProvider.class);
        when(provider.getClient()).thenReturn(oss);
        FileStorageServiceRouter router = new FileStorageServiceRouter(properties, provider);

        FileStorageService.UploadResult result = router.upload(
                new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES));

        assertTrue(result.getUrl().startsWith("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/"));
        assertEquals("image/png", result.getContentType());
        verify(oss).putObject(anyString(), anyString(), any(InputStream.class), any());
        assertTrue(!Files.exists(tempDir.resolve("product")), "配置 OSS 时不应落本地磁盘");
    }
}
