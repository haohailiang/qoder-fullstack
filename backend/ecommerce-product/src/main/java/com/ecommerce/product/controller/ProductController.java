package com.ecommerce.product.controller;

import com.ecommerce.common.Result;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
@Tag(name = "商品管理", description = "商品 CRUD、搜索、库存扣减等接口")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 查询所有商品 */
    @GetMapping
    @Operation(summary = "查询所有商品", description = "查询全部商品列表")
    public Result<List<Product>> getAllProducts() {
        return Result.success(productService.getAllProducts());
    }

    /** 根据 ID 查询商品 */
    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情", description = "根据商品 ID 查询单个商品详细信息")
    public Result<Product> getProductById(@Parameter(description = "商品 ID") @PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    /** 根据分类查询商品 */
    @GetMapping("/category/{category}")
    @Operation(summary = "按分类查询商品", description = "根据商品分类查询该分类下的所有商品")
    public Result<List<Product>> getProductsByCategory(@Parameter(description = "商品分类") @PathVariable String category) {
        return Result.success(productService.getProductsByCategory(category));
    }

    /** 搜索商品（支持关键词、分类、价格区间） */
    @GetMapping("/search")
    @Operation(summary = "搜索商品", description = "支持关键词、分类、价格区间等多条件搜索")
    public Result<List<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax) {
        return Result.success(productService.searchProducts(keyword, category, priceMin, priceMax));
    }

    /** 创建商品（管理接口，仅 ADMIN） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "创建商品", description = "管理接口，创建新商品，需要提供商品名称、价格、库存等信息")
    public Result<Product> createProduct(@Valid @RequestBody Product product) {
        return Result.success(productService.createProduct(product));
    }

    /** 更新商品（管理接口，仅 ADMIN） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新商品", description = "管理接口，根据商品 ID 更新商品信息")
    public Result<Product> updateProduct(@Parameter(description = "商品 ID") @PathVariable Long id, @Valid @RequestBody Product product) {
        return Result.success(productService.updateProduct(id, product));
    }

    /** 删除商品（管理接口，仅 ADMIN） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除商品", description = "管理接口，根据商品 ID 删除指定商品")
    public Result<Void> deleteProduct(@Parameter(description = "商品 ID") @PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success(null);
    }

    /** 全量同步商品到 ES（管理接口，仅 ADMIN） */
    @PostMapping("/sync-es")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "同步商品到 ES", description = "管理接口，全量同步所有商品数据到 Elasticsearch")
    public Result<Void> syncAllToEs() {
        productService.syncAllToElasticsearch();
        return Result.success(null);
    }

    /** 扣减库存（服务间内部接口，已由网关屏蔽外部访问，因此不加角色注解） */
    @PostMapping("/deduct-stock")
    @Operation(summary = "扣减库存", description = "扣减指定商品的库存数量，供订单服务 Feign 调用")
    public Result<Boolean> deductStock(@Parameter(description = "商品 ID") @RequestParam("productId") Long productId,
                                       @Parameter(description = "扣减数量") @RequestParam("quantity") Integer quantity) {
        boolean success = productService.deductStock(productId, quantity);
        return Result.success(success);
    }
}
