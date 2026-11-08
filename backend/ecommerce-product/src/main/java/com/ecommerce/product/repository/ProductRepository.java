package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.mapper.ProductMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class ProductRepository {

    private final ProductMapper productMapper;

    public ProductRepository(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<Product> findAll() {
        return productMapper.selectAll();
    }

    public Product findById(Long id) {
        return productMapper.selectById(id);
    }

    public List<Product> findByCategory(String category) {
        return productMapper.selectByCategory(category);
    }

    public List<Product> search(String keyword, String category, BigDecimal priceMin, BigDecimal priceMax) {
        return productMapper.searchProducts(keyword, category, priceMin, priceMax);
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
        return product;
    }

    public void deleteById(Long id) {
        productMapper.deleteById(id);
    }

    public int updateStock(Long id, Integer quantity) {
        return productMapper.updateStock(id, quantity);
    }
}
