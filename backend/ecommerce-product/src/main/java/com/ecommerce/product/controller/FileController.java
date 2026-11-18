package com.ecommerce.product.controller;

import com.ecommerce.common.Result;
import com.ecommerce.product.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口（管理接口，仅 ADMIN）。
 *
 * <p>图片经商品服务中转写入阿里云 OSS，对外路径形如 /api/product/files/images。
 */
@RestController
@RequestMapping("/files")
@Tag(name = "文件管理", description = "商品图片上传与删除（阿里云 OSS）")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /** 上传商品图片（仅 ADMIN） */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "上传图片", description = "上传商品图片到阿里云 OSS，返回可公开访问的 URL 与对象 Key，仅管理员可用")
    public Result<FileStorageService.UploadResult> uploadImage(
            @Parameter(description = "图片文件，支持 JPG/PNG/WEBP/GIF，不超过 5MB")
            @RequestPart("file") MultipartFile file) {
        return Result.success(fileStorageService.upload(file));
    }

    /** 删除商品图片（仅 ADMIN，运维用途） */
    @DeleteMapping("/images")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除图片", description = "按对象 Key 删除 OSS 中的图片，仅管理员可用，删除前会校验对象位于商品图片目录内")
    public Result<Void> deleteImage(
            @Parameter(description = "上传时返回的对象 Key，如 product/2026/11/18/xxx.png")
            @RequestParam("objectKey") String objectKey) {
        fileStorageService.delete(objectKey);
        return Result.success(null);
    }
}
