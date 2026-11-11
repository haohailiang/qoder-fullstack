package com.ecommerce.product.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeckillStockService 单元测试：验证 Redis + Lua 脚本实现的秒杀库存操作，
 * 包括库存初始化、预扣减、回滚、购买标记等核心逻辑。
 */
@ExtendWith(MockitoExtension.class)
class SeckillStockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeckillStockService seckillStockService;

    @BeforeEach
    void setUp() {
        // opsForValue 并非所有测试都用，使用 lenient 避免 UnnecessaryStubbing
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        seckillStockService = new SeckillStockService(redisTemplate);
    }

    // ──────────────────── initStock ────────────────────

    @Nested
    @DisplayName("initStock - 初始化秒杀库存")
    class InitStock {

        @Test
        @DisplayName("正常场景：将库存值写入 Redis")
        void shouldSetStockValueInRedis() {
            // When（void 方法在 mock 对象上默认什么都不做，无需 stub）
            seckillStockService.initStock(10L, 100);

            // Then
            verify(valueOperations).set("seckill:stock:10", "100");
        }

        @Test
        @DisplayName("边界场景：库存初始化为 0")
        void shouldSetZeroStock() {
            seckillStockService.initStock(10L, 0);

            verify(valueOperations).set("seckill:stock:10", "0");
        }
    }

    // ──────────────────── getStock ────────────────────

    @Nested
    @DisplayName("getStock - 获取当前库存")
    class GetStock {

        @Test
        @DisplayName("正常场景：库存已初始化时返回库存值")
        void shouldReturnStockWhenInitialized() {
            when(valueOperations.get("seckill:stock:10")).thenReturn("50");

            Integer stock = seckillStockService.getStock(10L);

            assertThat(stock).isEqualTo(50);
        }

        @Test
        @DisplayName("库存未初始化：返回 null")
        void shouldReturnNullWhenNotInitialized() {
            when(valueOperations.get("seckill:stock:10")).thenReturn(null);

            Integer stock = seckillStockService.getStock(10L);

            assertThat(stock).isNull();
        }
    }

    // ──────────────────── rollbackStock ────────────────────

    @Nested
    @DisplayName("rollbackStock - 回滚库存")
    class RollbackStock {

        @Test
        @DisplayName("正常场景：回滚指定数量的库存")
        void shouldIncrementStockByQuantity() {
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(55L);

            seckillStockService.rollbackStock(10L, 5);

            verify(valueOperations).increment("seckill:stock:10", 5L);
        }
    }

    // ──────────────────── markBought / hasBought / clearBought ────────────────────

    @Nested
    @DisplayName("购买标记 - markBought / hasBought / clearBought")
    class BoughtMark {

        @Test
        @DisplayName("markBought：标记用户已购买，设置 24 小时过期")
        void shouldMarkBoughtWith24HourExpiry() {
            // void 方法在 mock 对象上默认什么都不做，无需 stub
            seckillStockService.markBought(10L, 100L);

            verify(valueOperations).set("seckill:bought:10:100", "1", 24, TimeUnit.HOURS);
        }

        @Test
        @DisplayName("hasBought：用户已购买时返回 true")
        void shouldReturnTrueWhenBought() {
            when(redisTemplate.hasKey("seckill:bought:10:100")).thenReturn(true);

            boolean result = seckillStockService.hasBought(10L, 100L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("hasBought：用户未购买时返回 false")
        void shouldReturnFalseWhenNotBought() {
            when(redisTemplate.hasKey("seckill:bought:10:100")).thenReturn(false);

            boolean result = seckillStockService.hasBought(10L, 100L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("hasBought：Redis 返回 null 时视为未购买")
        void shouldReturnFalseWhenRedisReturnsNull() {
            when(redisTemplate.hasKey("seckill:bought:10:100")).thenReturn(null);

            boolean result = seckillStockService.hasBought(10L, 100L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("clearBought：清除用户购买标记")
        void shouldClearBoughtMark() {
            when(redisTemplate.delete(anyString())).thenReturn(true);

            seckillStockService.clearBought(10L, 100L);

            verify(redisTemplate).delete("seckill:bought:10:100");
        }
    }

    // ──────────────────── tryDecrStock ────────────────────

    @Nested
    @DisplayName("tryDecrStock - 预扣减库存（Lua 脚本）")
    class TryDecrStock {

        @Test
        @DisplayName("正常场景：执行 Lua 脚本扣减库存")
        void shouldExecuteLuaScript() {
            // Given
            when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                    .thenReturn(98L);

            // When
            Long remaining = seckillStockService.tryDecrStock(10L, 2);

            // Then
            assertThat(remaining).isEqualTo(98L);
        }

        @Test
        @DisplayName("库存不足：Lua 脚本返回 -2")
        void shouldReturnMinusTwoWhenStockInsufficient() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                    .thenReturn(SeckillStockService.RESULT_INSUFFICIENT);

            Long remaining = seckillStockService.tryDecrStock(10L, 100);

            assertThat(remaining).isEqualTo(-2L);
        }

        @Test
        @DisplayName("刚好售罄：Lua 脚本返回 0，属于扣减成功而非库存不足")
        void shouldReturnZeroWhenSoldOutExactly() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                    .thenReturn(0L);

            Long remaining = seckillStockService.tryDecrStock(10L, 1);

            assertThat(remaining).isZero();
            assertThat(remaining).isNotEqualTo(SeckillStockService.RESULT_INSUFFICIENT);
        }

        @Test
        @DisplayName("库存未初始化：Lua 脚本返回 -1")
        void shouldReturnMinusOneWhenStockNotInitialized() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                    .thenReturn(-1L);

            Long remaining = seckillStockService.tryDecrStock(10L, 1);

            assertThat(remaining).isEqualTo(-1L);
        }

        @Test
        @DisplayName("Redis 异常时返回 null")
        void shouldReturnNullWhenRedisFails() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                    .thenReturn(null);

            Long remaining = seckillStockService.tryDecrStock(10L, 1);

            assertThat(remaining).isNull();
        }
    }
}
