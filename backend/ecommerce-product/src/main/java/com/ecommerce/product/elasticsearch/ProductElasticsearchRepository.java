package com.ecommerce.product.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.math.BigDecimal;
import java.util.List;

public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, Long> {

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(String name, String description, Pageable pageable);

    Page<ProductDocument> findByCategory(String category, Pageable pageable);

    List<ProductDocument> findByIsSeckillTrueAndSeckillStartTimeBeforeAndSeckillEndTimeAfter(
            java.time.LocalDateTime startBefore, java.time.LocalDateTime endAfter);
}
