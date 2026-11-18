package com.ecommerce.product.controller;

import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.SeckillStockService;
import com.ecommerce.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FileController 集成测试：图片上传/删除接口的权限与错误码透传。
 *
 * <p>与 ProductControllerTest 一致，使用 @SpringBootTest + MockMvc，
 * GlobalExceptionHandler 不改变 HTTP 状态码，业务错误码体现在 Result.body.code。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private ProductService productService;

    @MockBean
    private SeckillStockService seckillStockService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private static RequestPostProcessor asAdmin() {
        return request -> {
            request.addHeader("X-User-Id", "1");
            request.addHeader("X-User-Name", "admin");
            request.addHeader("X-User-Role", "ADMIN");
            return request;
        };
    }

    private static RequestPostProcessor asPlainUser() {
        return request -> {
            request.addHeader("X-User-Id", "100");
            request.addHeader("X-User-Name", "zhangsan");
            request.addHeader("X-User-Role", "USER");
            return request;
        };
    }

    private static MockMultipartFile pngFile() {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        return new MockMultipartFile("file", "cover.png", "image/png", bytes);
    }

    @Nested
    @DisplayName("POST /files/images - 上传图片")
    class UploadImage {

        @Test
        @DisplayName("正常场景：管理员上传返回 URL 与对象 Key")
        void shouldUploadImage() throws Exception {
            FileStorageService.UploadResult result = new FileStorageService.UploadResult(
                    "https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/2026/11/18/a.png",
                    "product/2026/11/18/a.png", "cover.png", 12, "image/png");
            when(fileStorageService.upload(any())).thenReturn(result);

            mockMvc.perform(multipart("/files/images").file(pngFile()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.url").value(result.getUrl()))
                    .andExpect(jsonPath("$.data.objectKey").value(result.getObjectKey()));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 上传返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(multipart("/files/images").file(pngFile()).with(asPlainUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("异常场景：文件为空返回 400")
        void shouldReturn400WhenFileEmpty() throws Exception {
            when(fileStorageService.upload(any()))
                    .thenThrow(new BusinessException(400, "上传文件不能为空"));

            mockMvc.perform(multipart("/files/images")
                            .file(new MockMultipartFile("file", "", "application/octet-stream", new byte[0]))
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("上传文件不能为空"));
        }

        @Test
        @DisplayName("异常场景：超过 multipart 限制返回 400 友好提示")
        void shouldReturn400WhenFileExceedsMultipartLimit() throws Exception {
            when(fileStorageService.upload(any()))
                    .thenThrow(new MaxUploadSizeExceededException(5 * 1024 * 1024));

            mockMvc.perform(multipart("/files/images").file(pngFile()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("上传文件过大，单文件不能超过 5MB"));
        }

        @Test
        @DisplayName("异常场景：OSS 未配置返回 503")
        void shouldReturn503WhenOssNotConfigured() throws Exception {
            when(fileStorageService.upload(any()))
                    .thenThrow(new BusinessException(503, "OSS 未配置，请联系管理员"));

            mockMvc.perform(multipart("/files/images").file(pngFile()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(503));
        }
    }

    @Nested
    @DisplayName("DELETE /files/images - 删除图片")
    class DeleteImage {

        @Test
        @DisplayName("正常场景：管理员删除成功")
        void shouldDeleteImage() throws Exception {
            mockMvc.perform(delete("/files/images")
                            .param("objectKey", "product/2026/11/18/a.png")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：非法对象路径返回 400")
        void shouldReturn400WhenObjectKeyIllegal() throws Exception {
            doThrow(new BusinessException(400, "非法的对象路径"))
                    .when(fileStorageService).delete(eq("../../etc/passwd"));

            mockMvc.perform(delete("/files/images")
                            .param("objectKey", "../../etc/passwd")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("非法的对象路径"));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 删除返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(delete("/files/images")
                            .param("objectKey", "product/2026/11/18/a.png")
                            .with(asPlainUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }
}
