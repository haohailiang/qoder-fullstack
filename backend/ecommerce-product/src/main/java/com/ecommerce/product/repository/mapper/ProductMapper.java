package com.ecommerce.product.repository.mapper;

import com.ecommerce.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> selectAll();

    Product selectById(@Param("id") Long id);

    List<Product> selectByCategory(@Param("category") String category);

    List<Product> searchProducts(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("priceMin") java.math.BigDecimal priceMin,
                                 @Param("priceMax") java.math.BigDecimal priceMax);

    int insert(Product product);

    int updateById(Product product);

    int deleteById(@Param("id") Long id);

    int updateStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int updateImageUrl(@Param("id") Long id, @Param("imageUrl") String imageUrl);
}
