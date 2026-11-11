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

    /**
     * 秒杀库存回滚（订单落库失败进入死信后回调，恢复 Redis 预扣库存与购买标记）
     */
    @PostMapping("/seckill/rollback")
    Result<Void> rollbackSeckillStock(@RequestParam("productId") Long productId,
                                      @RequestParam("quantity") Integer quantity,
                                      @RequestParam("userId") Long userId);
}
