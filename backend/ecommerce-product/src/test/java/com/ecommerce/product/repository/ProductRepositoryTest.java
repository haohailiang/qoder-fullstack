package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.mapper.ProductMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductRepository 单元测试：验证 MyBatis Mapper 调用与数据转换逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductRepository productRepository;

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
        sampleProduct.setStatus(1);
        sampleProduct.setIsSeckill(0);
    }

    // ──────────────────── findAll ────────────────────

    @Nested
    @DisplayName("findAll - 查询全部商品")
    class FindAll {

        @Test
        @DisplayName("正常场景：返回全部商品列表")
        void shouldReturnAllProducts() {
            when(productMapper.selectAll()).thenReturn(List.of(sampleProduct));

            List<Product> result = productRepository.findAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("测试商品");
        }

        @Test
        @DisplayName("空数据：无商品时返回空列表")
        void shouldReturnEmptyList() {
            when(productMapper.selectAll()).thenReturn(Collections.emptyList());

            assertThat(productRepository.findAll()).isEmpty();
        }
    }

    // ──────────────────── findById ────────────────────

    @Nested
    @DisplayName("findById - 按 ID 查询商品")
    class FindById {

        @Test
        @DisplayName("正常场景：ID 存在时返回商品")
        void shouldReturnProductById() {
            when(productMapper.selectById(1L)).thenReturn(sampleProduct);

            Product result = productRepository.findById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("测试商品");
        }

        @Test
        @DisplayName("不存在：返回 null")
        void shouldReturnNullWhenNotFound() {
            when(productMapper.selectById(999L)).thenReturn(null);

            assertThat(productRepository.findById(999L)).isNull();
        }
    }

    // ──────────────────── findByCategory ────────────────────

    @Nested
    @DisplayName("findByCategory - 按分类查询商品")
    class FindByCategory {

        @Test
        @DisplayName("正常场景：返回指定分类的商品列表")
        void shouldReturnProductsByCategory() {
            when(productMapper.selectByCategory("电子产品")).thenReturn(List.of(sampleProduct));

            List<Product> result = productRepository.findByCategory("电子产品");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("电子产品");
        }

        @Test
        @DisplayName("空数据：指定分类无商品时返回空列表")
        void shouldReturnEmptyListForUnknownCategory() {
            when(productMapper.selectByCategory("不存在分类")).thenReturn(Collections.emptyList());

            assertThat(productRepository.findByCategory("不存在分类")).isEmpty();
        }
    }

    // ──────────────────── search ────────────────────

    @Nested
    @DisplayName("search - 多条件搜索商品")
    class Search {

        @Test
        @DisplayName("正常场景：关键词搜索返回匹配商品")
        void shouldReturnProductsByKeyword() {
            when(productMapper.searchProducts(eq("测试"), any(), any(), any()))
                    .thenReturn(List.of(sampleProduct));

            List<Product> result = productRepository.search("测试", null, null, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("正常场景：多条件搜索（关键词 + 分类 + 价格区间）")
        void shouldReturnProductsWithMultipleConditions() {
            when(productMapper.searchProducts(eq("测试"), eq("电子产品"),
                    eq(new BigDecimal("50")), eq(new BigDecimal("200"))))
                    .thenReturn(List.of(sampleProduct));

            List<Product> result = productRepository.search("测试", "电子产品",
                    new BigDecimal("50"), new BigDecimal("200"));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("空数据：无匹配商品时返回空列表")
        void shouldReturnEmptyListWhenNoMatch() {
            when(productMapper.searchProducts(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            assertThat(productRepository.search("不存在", null, null, null)).isEmpty();
        }
    }

    // ──────────────────── save ────────────────────

    @Nested
    @DisplayName("save - 保存商品（新增/更新）")
    class Save {

        @Test
        @DisplayName("新增商品：ID 为 null 时调用 insert")
        void shouldInsertWhenIdIsNull() {
            Product newProduct = new Product();
            newProduct.setName("新商品");
            newProduct.setPrice(new BigDecimal("199.99"));
            newProduct.setStock(50);
            // id == null → 走新增分支

            when(productMapper.insert(any(Product.class))).thenReturn(1);

            Product result = productRepository.save(newProduct);

            assertThat(result).isSameAs(newProduct);
            verify(productMapper).insert(newProduct);
        }

        @Test
        @DisplayName("更新商品：ID 不为 null 时调用 updateById")
        void shouldUpdateWhenIdIsNotNull() {
            sampleProduct.setName("更新后的商品");

            when(productMapper.updateById(any(Product.class))).thenReturn(1);

            Product result = productRepository.save(sampleProduct);

            assertThat(result).isSameAs(sampleProduct);
            verify(productMapper).updateById(sampleProduct);
        }

        @Test
        @DisplayName("新增商品：验证传入参数正确传递到 Mapper")
        void shouldPassCorrectProductToMapper() {
            Product newProduct = new Product();
            newProduct.setName("参数验证商品");
            newProduct.setPrice(new BigDecimal("299.99"));
            newProduct.setStock(30);
            newProduct.setCategory("数码");

            when(productMapper.insert(any(Product.class))).thenReturn(1);

            productRepository.save(newProduct);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).insert(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("参数验证商品");
            assertThat(captor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("299.99"));
        }
    }

    // ──────────────────── deleteById ────────────────────

    @Nested
    @DisplayName("deleteById - 删除商品")
    class DeleteById {

        @Test
        @DisplayName("正常场景：委托 Mapper 执行删除")
        void shouldDelegateDeleteToMapper() {
            when(productMapper.deleteById(1L)).thenReturn(1);

            productRepository.deleteById(1L);

            verify(productMapper).deleteById(1L);
        }
    }

    // ──────────────────── updateStock ────────────────────

    @Nested
    @DisplayName("updateStock - 扣减库存")
    class UpdateStock {

        @Test
        @DisplayName("正常场景：库存扣减成功返回受影响行数")
        void shouldReturnAffectedRowsOnSuccess() {
            when(productMapper.updateStock(1L, 2)).thenReturn(1);

            int affected = productRepository.updateStock(1L, 2);

            assertThat(affected).isEqualTo(1);
        }

        @Test
        @DisplayName("库存不足：返回受影响行数 0")
        void shouldReturnZeroWhenStockInsufficient() {
            when(productMapper.updateStock(1L, 100)).thenReturn(0);

            int affected = productRepository.updateStock(1L, 100);

            assertThat(affected).isEqualTo(0);
        }
    }
}
