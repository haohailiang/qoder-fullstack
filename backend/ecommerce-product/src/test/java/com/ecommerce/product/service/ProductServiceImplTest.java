package com.ecommerce.product.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.elasticsearch.ProductElasticsearchRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductServiceImpl 单元测试：覆盖商品 CRUD、库存扣减、ES 同步等核心业务逻辑，
 * 以及商品不存在、ES 异常等边界场景。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductElasticsearchRepository esRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setName("测试商品");
        sampleProduct.setDescription("这是一个测试商品");
        sampleProduct.setPrice(new BigDecimal("99.99"));
        sampleProduct.setStock(100);
        sampleProduct.setCategory("电子产品");
        sampleProduct.setImageUrl("http://example.com/image.jpg");
        sampleProduct.setStatus(1);
        sampleProduct.setIsSeckill(0);
    }

    // ──────────────────── getAllProducts ────────────────────

    @Nested
    @DisplayName("getAllProducts - 查询所有商品")
    class GetAllProducts {

        @Test
        @DisplayName("正常场景：返回全部商品列表")
        void shouldReturnAllProducts() {
            when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

            List<Product> result = productService.getAllProducts();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("测试商品");
        }

        @Test
        @DisplayName("空数据：无商品时返回空列表")
        void shouldReturnEmptyList() {
            when(productRepository.findAll()).thenReturn(Collections.emptyList());

            assertThat(productService.getAllProducts()).isEmpty();
        }
    }

    // ──────────────────── getProductById ────────────────────

    @Nested
    @DisplayName("getProductById - 根据 ID 查询商品")
    class GetProductById {

        @Test
        @DisplayName("正常场景：商品存在时返回详情")
        void shouldReturnProductById() {
            when(productRepository.findById(1L)).thenReturn(sampleProduct);

            Product result = productService.getProductById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("测试商品");
        }

        @Test
        @DisplayName("异常场景：商品不存在时抛出 404 业务异常")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("商品不存在: 999");
        }
    }

    // ──────────────────── getProductsByCategory ────────────────────

    @Nested
    @DisplayName("getProductsByCategory - 按分类查询商品")
    class GetProductsByCategory {

        @Test
        @DisplayName("正常场景：返回指定分类的商品列表")
        void shouldReturnProductsByCategory() {
            when(productRepository.findByCategory("电子产品")).thenReturn(List.of(sampleProduct));

            List<Product> result = productService.getProductsByCategory("电子产品");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("电子产品");
        }

        @Test
        @DisplayName("空数据：指定分类无商品时返回空列表")
        void shouldReturnEmptyListForUnknownCategory() {
            when(productRepository.findByCategory("不存在分类")).thenReturn(Collections.emptyList());

            assertThat(productService.getProductsByCategory("不存在分类")).isEmpty();
        }
    }

    // ──────────────────── searchProducts ────────────────────

    @Nested
    @DisplayName("searchProducts - 搜索商品")
    class SearchProducts {

        @Test
        @DisplayName("正常场景：关键词搜索返回匹配商品")
        void shouldReturnProductsByKeyword() {
            when(productRepository.search(eq("测试"), any(), any(), any()))
                    .thenReturn(List.of(sampleProduct));

            List<Product> result = productService.searchProducts("测试", null, null, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("正常场景：多条件搜索（关键词 + 分类 + 价格区间）")
        void shouldReturnProductsWithMultipleConditions() {
            when(productRepository.search(eq("测试"), eq("电子产品"),
                    eq(new BigDecimal("50")), eq(new BigDecimal("200"))))
                    .thenReturn(List.of(sampleProduct));

            List<Product> result = productService.searchProducts("测试", "电子产品",
                    new BigDecimal("50"), new BigDecimal("200"));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("空数据：无匹配商品时返回空列表")
        void shouldReturnEmptyListWhenNoMatch() {
            when(productRepository.search(eq("不存在"), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            assertThat(productService.searchProducts("不存在", null, null, null)).isEmpty();
        }
    }

    // ──────────────────── createProduct ────────────────────

    @Nested
    @DisplayName("createProduct - 创建商品")
    class CreateProduct {

        @Test
        @DisplayName("正常场景：创建商品并同步到 ES")
        void shouldCreateProductAndSyncToEs() {
            // Given
            Product newProduct = new Product();
            newProduct.setName("新商品");
            newProduct.setPrice(new BigDecimal("199.99"));
            newProduct.setStock(50);
            newProduct.setCategory("数码");

            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(10L);
                return saved;
            });
            when(productRepository.findById(10L)).thenReturn(newProduct);
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            // When
            Product result = productService.createProduct(newProduct);

            // Then
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getStatus()).isEqualTo(1); // 默认上架
            assertThat(result.getIsSeckill()).isEqualTo(0);
            assertThat(result.getCreatedDate()).isNotNull();
            verify(esRepository).save(any());
        }

        @Test
        @DisplayName("默认值：status 为 null 时自动设为 1（上架）")
        void shouldSetDefaultStatus() {
            Product newProduct = new Product();
            newProduct.setName("新商品");
            newProduct.setStatus(null);

            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(11L);
                return saved;
            });
            when(productRepository.findById(11L)).thenReturn(newProduct);
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            Product result = productService.createProduct(newProduct);

            assertThat(result.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("ES 未启用时跳过同步（esRepository == null）")
        void shouldSkipEsSyncWhenEsNotEnabled() {
            // 创建一个没有 ES 的服务实例
            ProductServiceImpl serviceWithoutEs = new ProductServiceImpl(productRepository, null);

            Product newProduct = new Product();
            newProduct.setName("新商品");

            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(12L);
                return saved;
            });

            Product result = serviceWithoutEs.createProduct(newProduct);

            assertThat(result.getId()).isEqualTo(12L);
            // esRepository 为 null，不应调用 ES 同步
            verify(esRepository, never()).save(any());
        }
    }

    // ──────────────────── updateProduct ────────────────────

    @Nested
    @DisplayName("updateProduct - 更新商品")
    class UpdateProduct {

        @Test
        @DisplayName("正常场景：更新已有商品信息并同步到 ES")
        void shouldUpdateProductAndSyncToEs() {
            // Given
            Product updatePayload = new Product();
            updatePayload.setName("更新后的商品");
            updatePayload.setPrice(new BigDecimal("299.99"));
            updatePayload.setStock(80);

            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            // When
            Product result = productService.updateProduct(1L, updatePayload);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLastModifiedDate()).isNotNull();
        }

        @Test
        @DisplayName("异常场景：更新不存在的商品抛出 404")
        void shouldThrowWhenProductNotFoundForUpdate() {
            when(productRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> productService.updateProduct(999L, new Product()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404);
        }
    }

    // ──────────────────── deleteProduct ────────────────────

    @Nested
    @DisplayName("deleteProduct - 删除商品")
    class DeleteProduct {

        @Test
        @DisplayName("正常场景：删除已有商品，同步删除 ES 文档")
        void shouldDeleteProductAndRemoveFromEs() {
            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            doNothing().when(productRepository).deleteById(1L);
            doNothing().when(esRepository).deleteById(1L);

            productService.deleteProduct(1L);

            verify(productRepository).deleteById(1L);
            verify(esRepository).deleteById(1L);
        }

        @Test
        @DisplayName("异常场景：删除不存在的商品抛出 404")
        void shouldThrowWhenProductNotFoundForDelete() {
            when(productRepository.findById(999L)).thenReturn(null);

            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404);

            verify(productRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("ES 删除异常时不影响主流程（仅记录警告日志）")
        void shouldNotFailWhenEsDeleteFails() {
            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            doNothing().when(productRepository).deleteById(1L);
            doThrow(new RuntimeException("ES connection failed")).when(esRepository).deleteById(1L);

            // 不应抛出异常
            productService.deleteProduct(1L);

            verify(productRepository).deleteById(1L);
        }
    }

    // ──────────────────── deductStock ────────────────────

    @Nested
    @DisplayName("deductStock - 扣减库存")
    class DeductStock {

        @Test
        @DisplayName("正常场景：库存扣减成功返回 true")
        void shouldReturnTrueWhenStockDeducted() {
            when(productRepository.updateStock(1L, 2)).thenReturn(1);
            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            boolean result = productService.deductStock(1L, 2);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("库存不足：扣减失败返回 false")
        void shouldReturnFalseWhenStockInsufficient() {
            when(productRepository.updateStock(1L, 100)).thenReturn(0);

            boolean result = productService.deductStock(1L, 100);

            assertThat(result).isFalse();
        }
    }

    // ──────────────────── syncToElasticsearch ────────────────────

    @Nested
    @DisplayName("syncToElasticsearch - 同步商品到 ES")
    class SyncToElasticsearch {

        @Test
        @DisplayName("正常场景：商品存在时同步到 ES")
        void shouldSyncProductToEs() {
            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            productService.syncToElasticsearch(1L);

            ArgumentCaptor<com.ecommerce.product.elasticsearch.ProductDocument> captor =
                    ArgumentCaptor.forClass(com.ecommerce.product.elasticsearch.ProductDocument.class);
            verify(esRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("测试商品");
        }

        @Test
        @DisplayName("商品不存在时跳过同步")
        void shouldSkipSyncWhenProductNotFound() {
            when(productRepository.findById(999L)).thenReturn(null);

            productService.syncToElasticsearch(999L);

            verify(esRepository, never()).save(any());
        }

        @Test
        @DisplayName("ES 异常时不影响主流程（仅记录错误日志）")
        void shouldNotFailWhenEsSyncThrows() {
            when(productRepository.findById(1L)).thenReturn(sampleProduct);
            doThrow(new RuntimeException("ES bulk request failed")).when(esRepository).save(any());

            // 不应抛出异常
            productService.syncToElasticsearch(1L);
        }
    }

    // ──────────────────── syncAllToElasticsearch ────────────────────

    @Nested
    @DisplayName("syncAllToElasticsearch - 全量同步到 ES")
    class SyncAllToElasticsearch {

        @Test
        @DisplayName("正常场景：全量同步所有商品到 ES")
        void shouldSyncAllProductsToEs() {
            Product product2 = new Product();
            product2.setId(2L);
            product2.setName("商品2");
            product2.setPrice(new BigDecimal("199.99"));
            product2.setStock(50);
            product2.setCategory("数码");
            product2.setStatus(1);
            product2.setIsSeckill(0);

            when(productRepository.findAll()).thenReturn(List.of(sampleProduct, product2));
            // esRepository.save() 非 void，mock 默认返回 null 即可，无需 stub

            productService.syncAllToElasticsearch();

            verify(esRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("ES 未启用时跳过全量同步")
        void shouldSkipWhenEsNotEnabled() {
            ProductServiceImpl serviceWithoutEs = new ProductServiceImpl(productRepository, null);

            serviceWithoutEs.syncAllToElasticsearch();

            // 不抛异常即为通过
            verify(productRepository, never()).findAll();
        }

        @Test
        @DisplayName("部分商品同步失败时继续处理其余商品")
        void shouldContinueWhenSomeSyncFails() {
            Product product2 = new Product();
            product2.setId(2L);
            product2.setName("商品2");
            product2.setPrice(new BigDecimal("199.99"));
            product2.setStock(50);
            product2.setCategory("数码");
            product2.setStatus(1);
            product2.setIsSeckill(0);

            when(productRepository.findAll()).thenReturn(List.of(sampleProduct, product2));
            // 第一次保存抛异常，第二次正常
            doThrow(new RuntimeException("ES error")).when(esRepository).save(any());

            productService.syncAllToElasticsearch();

            verify(esRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }
}
