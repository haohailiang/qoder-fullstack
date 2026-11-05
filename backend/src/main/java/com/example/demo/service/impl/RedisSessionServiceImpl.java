package com.example.demo.service.impl;

import com.example.demo.service.RedisSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 会话管理服务实现。
 * 
 * <p>实现要点：
 * <ul>
 *   <li>Token 存储使用 {@code SET key value EX ttl} 命令，原子性地设置值和过期时间</li>
 *   <li>分布式锁使用 {@code SETNX} 实现，若 key 已存在则说明有其他登录正在进行</li>
 *   <li>锁的 TTL 设置为 10 秒，防止异常情况导致锁无法释放</li>
 * </ul>
 * 
 * <p>Redis Key 前缀：
 * <ul>
 *   <li>{@code login:token:} - 用户会话 Token</li>
 *   <li>{@code login:lock:} - 登录并发锁</li>
 * </ul>
 */
@Service
public class RedisSessionServiceImpl implements RedisSessionService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionServiceImpl.class);

    /** 用户会话 Token 的 Key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    /** 登录并发锁的 Key 前缀 */
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    /** 登录锁的 TTL（秒），防止异常情况导致锁无法释放 */
    private static final long LOCK_TTL_SECONDS = 10;

    private final StringRedisTemplate redisTemplate;

    public RedisSessionServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveToken(Long userId, String token, long ttlSeconds) {
        String key = TOKEN_KEY_PREFIX + userId;
        // 使用 SET + EX 原子性地设置 Token 和过期时间
        // 这会覆盖该用户的旧 Token，使旧设备的会话自动失效
        redisTemplate.opsForValue().set(key, token, ttlSeconds, TimeUnit.SECONDS);
        log.debug("已保存用户 {} 的 Token，TTL={}秒", userId, ttlSeconds);
    }

    @Override
    public String getToken(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void removeToken(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("已删除用户 {} 的 Token，删除成功={}", userId, deleted);
    }

    @Override
    public boolean validateToken(Long userId, String token) {
        String latestToken = getToken(userId);
        if (latestToken == null) {
            // Redis 中无记录，可能是未登录或会话已过期
            log.debug("用户 {} 在 Redis 中无 Token 记录", userId);
            return false;
        }
        // 比对请求 Token 与 Redis 中最新 Token 是否完全一致
        boolean valid = latestToken.equals(token);
        if (!valid) {
            log.info("用户 {} 的 Token 校验失败，可能已被其他设备挤下线", userId);
        }
        return valid;
    }

    @Override
    public boolean tryAcquireLoginLock(Long userId) {
        String lockKey = LOCK_KEY_PREFIX + userId;
        // 使用 SETNX 尝试获取锁
        // setIfAbsent 对应 Redis 的 SETNX 命令，若 key 不存在则设置成功返回 true
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        boolean success = Boolean.TRUE.equals(acquired);
        if (success) {
            log.debug("用户 {} 获取登录锁成功", userId);
        } else {
            log.warn("用户 {} 获取登录锁失败，可能有并发登录请求", userId);
        }
        return success;
    }

    @Override
    public void releaseLoginLock(Long userId) {
        String lockKey = LOCK_KEY_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(lockKey);
        log.debug("用户 {} 释放登录锁，删除成功={}", userId, deleted);
    }
}
