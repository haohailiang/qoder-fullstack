package com.ecommerce.product.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.elasticsearch.ProductDocument;
import com.ecommerce.product.elasticsearch.ProductElasticsearchRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductElasticsearchRepository esRepository;

    public ProductServiceImpl(ProductRepository productRepository,
                              @Autowired(required = false) ProductElasticsearchRepository esRepository) {
        this.productRepository = productRepository;
        this.esRepository = esRepository;
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Product getProductById(Long id) {
        Product product = productRepository.findById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在: " + id);
        }
        return product;
    }

    @Override
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Override
    public List<Product> searchProducts(String keyword, String category, BigDecimal priceMin, BigDecimal priceMax) {
        return productRepository.search(keyword, category, priceMin, priceMax);
    }

    @Override
    @Transactional
    public Product createProduct(Product product) {
        product.setCreatedDate(LocalDateTime.now());
        if (product.getStatus() == null) {
            product.setStatus(1); // 默认上架
        }
        if (product.getIsSeckill() == null) {
            product.setIsSeckill(0);
        }
        Product saved = productRepository.save(product);
        // 同步到 ES
        syncToElasticsearch(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, Product product) {
        Product existing = getProductById(id);
        product.setId(existing.getId());
        product.setLastModifiedDate(LocalDateTime.now());
        Product updated = productRepository.save(product);
        // 同步到 ES
        syncToElasticsearch(updated.getId());
        return updated;
    }

    @Override
    @Transactional
    public Product updateProductImage(Long id, String imageUrl) {
        Product existing = getProductById(id);
        String normalized = imageUrl == null ? "" : imageUrl.trim();
        productRepository.updateImageUrl(id, normalized);
        existing.setImageUrl(normalized);
        // 图片变更后同步到 ES，保证搜索结果中的图片即时生效
        syncToElasticsearch(id);
        return existing;
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        getProductById(id); // 确认存在
        productRepository.deleteById(id);
        // 从 ES 删除
        if (esRepository != null) {
            try {
                esRepository.deleteById(id);
            } catch (Exception e) {
                log.warn("ES 删除商品失败: id={}, error={}", id, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public boolean deductStock(Long productId, Integer quantity) {
        int affected = productRepository.updateStock(productId, quantity);
        if (affected > 0) {
            // 库存变动后同步到 ES
            syncToElasticsearch(productId);
            return true;
        }
        return false;
    }

    @Override
    public void syncToElasticsearch(Long productId) {
        if (esRepository == null) {
            log.debug("ES 未启用，跳过商品同步: id={}", productId);
            return;
        }
        try {
            Product product = productRepository.findById(productId);
            if (product != null) {
                ProductDocument doc = toDocument(product);
                esRepository.save(doc);
                log.debug("商品同步到 ES 成功: id={}", productId);
            }
        } catch (Exception e) {
            log.error("商品同步到 ES 失败: id={}, error={}", productId, e.getMessage(), e);
        }
    }

    @Override
    public void syncAllToElasticsearch() {
        if (esRepository == null) {
            log.info("ES 未启用，跳过全量同步");
            return;
        }
        List<Product> allProducts = productRepository.findAll();
        for (Product product : allProducts) {
            try {
                esRepository.save(toDocument(product));
            } catch (Exception e) {
                log.error("ES 同步失败: productId={}, error={}", product.getId(), e.getMessage());
            }
        }
        log.info("全量同步商品到 ES 完成，共 {} 条", allProducts.size());
    }

    private ProductDocument toDocument(Product product) {
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId());
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setStock(product.getStock());
        doc.setCategory(product.getCategory());
        doc.setImageUrl(product.getImageUrl());
        doc.setStatus(product.getStatus());
        doc.setIsSeckill(product.getIsSeckill() != null && product.getIsSeckill() == 1);
        doc.setSeckillPrice(product.getSeckillPrice());
        doc.setSeckillStartTime(product.getSeckillStartTime());
        doc.setSeckillEndTime(product.getSeckillEndTime());
        return doc;
    }
}
