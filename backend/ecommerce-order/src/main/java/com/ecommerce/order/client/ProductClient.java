package com.ecommerce.order.client;

import com.ecommerce.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端
 */
@FeignClient(name = "ecommerce-product", path = "/product/products")
public interface ProductClient {

    /**
     * 扣减库存
     */
    @PostMapping("/deduct-stock")
    Result<Boolean> deductStock(@RequestParam("productId") Long productId,
                                @RequestParam("quantity") Integer quantity);
}
