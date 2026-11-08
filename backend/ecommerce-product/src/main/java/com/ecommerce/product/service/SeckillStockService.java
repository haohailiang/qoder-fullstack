package com.ecommerce.product.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存服务：基于 Redis + Lua 脚本实现原子性预扣减库存
 */
@Service
public class SeckillStockService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String BOUGHT_KEY_PREFIX = "seckill:bought:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;

    public SeckillStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.seckillScript = new DefaultRedisScript<>();
        this.seckillScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/seckill_stock.lua")));
        this.seckillScript.setResultType(Long.class);
    }

    /**
     * 初始化秒杀库存（秒杀开始前将库存写入 Redis）
     */
    public void initStock(Long productId, Integer stock) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(stock));
    }

    /**
     * 尝试预扣减库存（Lua 脚本保证原子性）
     * @return 剩余库存量，-1=库存未初始化，0=库存不足
     */
    public Long tryDecrStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        return redisTemplate.execute(seckillScript,
                Collections.singletonList(key),
                String.valueOf(quantity));
    }

    /**
     * 回滚库存（消费失败时恢复）
     */
    public void rollbackStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(key, quantity);
    }

    /**
     * 获取当前库存
     */
    public Integer getStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : null;
    }

    /**
     * 标记用户已购买（防止重复购买）
     */
    public void markBought(Long productId, Long userId) {
        String key = BOUGHT_KEY_PREFIX + productId + ":" + userId;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 检查用户是否已购买
     */
    public boolean hasBought(Long productId, Long userId) {
        String key = BOUGHT_KEY_PREFIX + productId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 清除购买标记（回滚时调用）
     */
    public void clearBought(Long productId, Long userId) {
        String key = BOUGHT_KEY_PREFIX + productId + ":" + userId;
        redisTemplate.delete(key);
    }
}
