package com.ecommerce.product.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.SeckillStockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductController 集成测试：通过 @SpringBootTest + @AutoConfigureMockMvc
 * 验证 HTTP 请求/响应（状态码、返回格式），使用 @MockBean 模拟 Service 层依赖。
 * 注意：GlobalExceptionHandler 未标注 @ResponseStatus，HTTP 状态恒为 200，
 * 真正的错误码在 Result.body.code 中体现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private SeckillStockService seckillStockService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    /**
     * 商品写接口已标注 @PreAuthorize("hasRole('ADMIN')")，
     * 角色由 TrustedAuthenticationFilter 从网关注入的 X-User-Role 转为 ROLE_ADMIN。
     */
    private static RequestPostProcessor asAdmin() {
        return request -> {
            request.addHeader("X-User-Id", "1");
            request.addHeader("X-User-Name", "admin");
            request.addHeader("X-User-Role", "ADMIN");
            return request;
        };
    }

    /** 普通登录用户，用于验证管理接口对非 ADMIN 直接拒绝 */
    private static RequestPostProcessor asPlainUser() {
        return request -> {
            request.addHeader("X-User-Id", "100");
            request.addHeader("X-User-Name", "zhangsan");
            request.addHeader("X-User-Role", "USER");
            return request;
        };
    }

    // ──────────────────── GET /products ────────────────────

    @Nested
    @DisplayName("GET /products - 查询所有商品")
    class GetAllProducts {

        @Test
        @DisplayName("正常场景：返回商品列表")
        void shouldReturnProductList() throws Exception {
            Product product = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            when(productService.getAllProducts()).thenReturn(List.of(product));

            mockMvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].name").value("测试商品"));
        }

        @Test
        @DisplayName("空列表：返回空数组")
        void shouldReturnEmptyArray() throws Exception {
            when(productService.getAllProducts()).thenReturn(List.of());

            mockMvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ──────────────────── GET /products/{id} ────────────────────

    @Nested
    @DisplayName("GET /products/{id} - 查询商品详情")
    class GetProductById {

        @Test
        @DisplayName("正常场景：商品存在时返回详情")
        void shouldReturnProduct() throws Exception {
            Product product = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            when(productService.getProductById(1L)).thenReturn(product);

            mockMvc.perform(get("/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("测试商品"));
        }

        @Test
        @DisplayName("异常场景：商品不存在返回 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(productService.getProductById(999L))
                    .thenThrow(new BusinessException(404, "商品不存在: 999"));

            mockMvc.perform(get("/products/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("商品不存在: 999"));
        }
    }

    // ──────────────────── GET /products/category/{category} ────────────────────

    @Nested
    @DisplayName("GET /products/category/{category} - 按分类查询商品")
    class GetProductsByCategory {

        @Test
        @DisplayName("正常场景：返回指定分类的商品列表")
        void shouldReturnProductsByCategory() throws Exception {
            Product product = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            when(productService.getProductsByCategory("电子产品")).thenReturn(List.of(product));

            mockMvc.perform(get("/products/category/电子产品"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].category").value("电子产品"));
        }
    }

    // ──────────────────── GET /products/search ────────────────────

    @Nested
    @DisplayName("GET /products/search - 搜索商品")
    class SearchProducts {

        @Test
        @DisplayName("正常场景：关键词搜索返回匹配商品")
        void shouldReturnProductsByKeyword() throws Exception {
            Product product = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            when(productService.searchProducts(eq("测试"), any(), any(), any()))
                    .thenReturn(List.of(product));

            mockMvc.perform(get("/products/search").param("keyword", "测试"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].name").value("测试商品"));
        }

        @Test
        @DisplayName("正常场景：多条件搜索（关键词 + 分类 + 价格区间）")
        void shouldReturnProductsWithMultipleConditions() throws Exception {
            Product product = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            when(productService.searchProducts(eq("测试"), eq("电子产品"), any(), any()))
                    .thenReturn(List.of(product));

            mockMvc.perform(get("/products/search")
                            .param("keyword", "测试")
                            .param("category", "电子产品")
                            .param("priceMin", "50")
                            .param("priceMax", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ──────────────────── POST /products ────────────────────

    @Nested
    @DisplayName("POST /products - 创建商品")
    class CreateProduct {

        @Test
        @DisplayName("正常场景：创建商品成功")
        void shouldCreateProduct() throws Exception {
            Product created = buildProduct(10L, "新商品", new BigDecimal("199.99"));
            when(productService.createProduct(any(Product.class))).thenReturn(created);

            String body = """
                    {"name":"新商品","price":199.99,"stock":50,"category":"数码"}
                    """;

            mockMvc.perform(post("/products")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.name").value("新商品"));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 创建商品返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(post("/products")
                            .with(asPlainUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"越权商品","price":1.00,"stock":1}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ──────────────────── PUT /products/{id} ────────────────────

    @Nested
    @DisplayName("PUT /products/{id} - 更新商品")
    class UpdateProduct {

        @Test
        @DisplayName("正常场景：更新商品成功")
        void shouldUpdateProduct() throws Exception {
            Product updated = buildProduct(1L, "更新后的商品", new BigDecimal("299.99"));
            when(productService.updateProduct(eq(1L), any(Product.class))).thenReturn(updated);

            String body = """
                    {"name":"更新后的商品","price":299.99,"stock":80}
                    """;

            mockMvc.perform(put("/products/1")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("更新后的商品"));
        }

        @Test
        @DisplayName("异常场景：更新不存在的商品返回 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(productService.updateProduct(eq(999L), any(Product.class)))
                    .thenThrow(new BusinessException(404, "商品不存在: 999"));

            String body = """
                    {"name":"更新","price":299.99,"stock":80}
                    """;

            mockMvc.perform(put("/products/999")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("商品不存在: 999"));
        }
    }

    // ──────────────────── PUT /products/{id}/image ────────────────────

    @Nested
    @DisplayName("PUT /products/{id}/image - 更新商品图片")
    class UpdateProductImage {

        @Test
        @DisplayName("正常场景：更新图片成功")
        void shouldUpdateProductImage() throws Exception {
            Product updated = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            updated.setImageUrl("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/a.png");
            when(productService.updateProductImage(eq(1L), eq(updated.getImageUrl()))).thenReturn(updated);

            mockMvc.perform(put("/products/1/image")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/a.png"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.imageUrl").value(updated.getImageUrl()));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 更新图片返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(put("/products/1/image")
                            .with(asPlainUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/a.png"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("异常场景：商品不存在返回 404")
        void shouldReturn404WhenNotFound() throws Exception {
            when(productService.updateProductImage(eq(999L), any()))
                    .thenThrow(new BusinessException(404, "商品不存在: 999"));

            mockMvc.perform(put("/products/999/image")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/a.png"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("校验场景：非 http(s) URL 返回 400")
        void shouldReturn400WhenUrlInvalid() throws Exception {
            mockMvc.perform(put("/products/1/image")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"javascript:alert(1)"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("校验场景：协议相对地址 //evil.com 返回 400")
        void shouldReturn400WhenProtocolRelativeUrl() throws Exception {
            mockMvc.perform(put("/products/1/image")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"//evil.com/a.png"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("正常场景：本地存储返回的站内相对路径可回写")
        void shouldAcceptLocalRelativeUrl() throws Exception {
            String localUrl = "/api/product/files/local/product/2026/11/18/a.png";
            Product updated = buildProduct(1L, "测试商品", new BigDecimal("99.99"));
            updated.setImageUrl(localUrl);
            when(productService.updateProductImage(eq(1L), eq(localUrl))).thenReturn(updated);

            mockMvc.perform(put("/products/1/image")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageUrl":"/api/product/files/local/product/2026/11/18/a.png"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.imageUrl").value(localUrl));
        }
    }

    // ──────────────────── DELETE /products/{id} ────────────────────

    @Nested
    @DisplayName("DELETE /products/{id} - 删除商品")
    class DeleteProduct {

        @Test
        @DisplayName("正常场景：删除商品成功")
        void shouldDeleteProduct() throws Exception {
            doNothing().when(productService).deleteProduct(1L);

            mockMvc.perform(delete("/products/1")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 删除商品返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(delete("/products/1")
                            .with(asPlainUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("异常场景：删除不存在的商品返回 404")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new BusinessException(404, "商品不存在: 999"))
                    .when(productService).deleteProduct(999L);

            mockMvc.perform(delete("/products/999")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("商品不存在: 999"));
        }
    }

    // ──────────────────── POST /products/deduct-stock ────────────────────

    @Nested
    @DisplayName("POST /products/deduct-stock - 扣减库存")
    class DeductStock {

        @Test
        @DisplayName("正常场景：库存扣减成功返回 true")
        void shouldReturnTrueWhenStockDeducted() throws Exception {
            when(productService.deductStock(1L, 2)).thenReturn(true);

            mockMvc.perform(post("/products/deduct-stock")
                            .param("productId", "1")
                            .param("quantity", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("库存不足：返回 false")
        void shouldReturnFalseWhenStockInsufficient() throws Exception {
            when(productService.deductStock(1L, 100)).thenReturn(false);

            mockMvc.perform(post("/products/deduct-stock")
                            .param("productId", "1")
                            .param("quantity", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("内部接口：普通用户身份调用也不受管理接口权限限制")
        void shouldAllowCallWithoutAdminRole() throws Exception {
            when(productService.deductStock(1L, 1)).thenReturn(true);

            mockMvc.perform(post("/products/deduct-stock")
                            .with(asPlainUser())
                            .param("productId", "1")
                            .param("quantity", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    // ──────────────────── POST /products/sync-es ────────────────────

    @Nested
    @DisplayName("POST /products/sync-es - 全量同步商品到 ES")
    class SyncAllToEs {

        @Test
        @DisplayName("正常场景：全量同步成功")
        void shouldSyncAllToEs() throws Exception {
            doNothing().when(productService).syncAllToElasticsearch();

            mockMvc.perform(post("/products/sync-es")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ──────────────────── helper ────────────────────

    private Product buildProduct(Long id, String name, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription(name + "的描述");
        product.setPrice(price);
        product.setStock(100);
        product.setCategory("电子产品");
        product.setStatus(1);
        product.setIsSeckill(0);
        return product;
    }
}
