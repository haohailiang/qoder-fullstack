package com.ecommerce.product.controller;

import com.ecommerce.common.Result;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 查询所有商品 */
    @GetMapping
    public Result<List<Product>> getAllProducts() {
        return Result.success(productService.getAllProducts());
    }

    /** 根据 ID 查询商品 */
    @GetMapping("/{id}")
    public Result<Product> getProductById(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    /** 根据分类查询商品 */
    @GetMapping("/category/{category}")
    public Result<List<Product>> getProductsByCategory(@PathVariable String category) {
        return Result.success(productService.getProductsByCategory(category));
    }

    /** 搜索商品（支持关键词、分类、价格区间） */
    @GetMapping("/search")
    public Result<List<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax) {
        return Result.success(productService.searchProducts(keyword, category, priceMin, priceMax));
    }

    /** 创建商品 */
    @PostMapping
    public Result<Product> createProduct(@Valid @RequestBody Product product) {
        return Result.success(productService.createProduct(product));
    }

    /** 更新商品 */
    @PutMapping("/{id}")
    public Result<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        return Result.success(productService.updateProduct(id, product));
    }

    /** 删除商品 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success(null);
    }

    /** 全量同步商品到 ES */
    @PostMapping("/sync-es")
    public Result<Void> syncAllToEs() {
        productService.syncAllToElasticsearch();
        return Result.success(null);
    }

    /** 扣减库存（供订单服务 Feign 调用） */
    @PostMapping("/deduct-stock")
    public Result<Boolean> deductStock(@RequestParam("productId") Long productId,
                                       @RequestParam("quantity") Integer quantity) {
        boolean success = productService.deductStock(productId, quantity);
        return Result.success(success);
    }
}
