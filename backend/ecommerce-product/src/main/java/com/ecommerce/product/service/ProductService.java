package com.ecommerce.product.service;

import com.ecommerce.product.entity.Product;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    List<Product> getAllProducts();

    Product getProductById(Long id);

    List<Product> getProductsByCategory(String category);

    List<Product> searchProducts(String keyword, String category, BigDecimal priceMin, BigDecimal priceMax);

    Product createProduct(Product product);

    Product updateProduct(Long id, Product product);

    /**
     * 仅更新商品图片 URL（空字符串表示清除图片）
     */
    Product updateProductImage(Long id, String imageUrl);

    void deleteProduct(Long id);

    /**
     * 扣减库存（用于普通购买）
     */
    boolean deductStock(Long productId, Integer quantity);

    /**
     * 同步商品数据到 Elasticsearch
     */
    void syncToElasticsearch(Long productId);

    /**
     * 全量同步所有商品到 ES
     */
    void syncAllToElasticsearch();
}
