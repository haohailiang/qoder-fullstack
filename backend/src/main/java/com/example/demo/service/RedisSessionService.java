package com.example.demo.service;

/**
 * Redis 会话管理服务接口。
 * 用于存储用户最新的有效 JWT Token，实现单设备登录（互踢/挤下线）机制。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>登录时将新 Token 写入 Redis（覆盖旧 Token，使旧设备自动失效）</li>
 *   <li>鉴权时校验请求 Token 与 Redis 中最新 Token 是否一致</li>
 *   <li>登出时清除 Redis 中的 Token</li>
 *   <li>分布式锁防止同一账号并发登录导致会话状态错乱</li>
 * </ul>
 * 
 * <p>Redis Key 设计：
 * <ul>
 *   <li>{@code login:token:{userId}} - 存储用户最新有效 Token，TTL 与 JWT 有效期一致</li>
 *   <li>{@code login:lock:{userId}} - 登录并发分布式锁，TTL 10 秒</li>
 * </ul>
 */
public interface RedisSessionService {

    /**
     * 保存用户 Token（登录时调用）。
     * 若该用户已有旧 Token，将被覆盖，旧设备下次请求时会因 Token 不匹配而被踢出。
     *
     * @param userId     用户 ID
     * @param token      JWT Token 字符串
     * @param ttlSeconds 过期时间（秒），通常与 JWT 有效期保持一致
     */
    void saveToken(Long userId, String token, long ttlSeconds);

    /**
     * 获取用户最新 Token。
     *
     * @param userId 用户 ID
     * @return 最新有效的 JWT Token，若用户未登录或已过期则返回 null
     */
    String getToken(Long userId);

    /**
     * 删除用户 Token（登出时调用）。
     * 删除后该用户的所有设备都需要重新登录。
     *
     * @param userId 用户 ID
     */
    void removeToken(Long userId);

    /**
     * 校验 Token 是否为当前有效 Token。
     * 用于鉴权阶段判断请求是否来自已被挤下线的旧设备。
     *
     * @param userId 用户 ID
     * @param token  请求中携带的 JWT Token
     * @return true=Token 有效（与 Redis 中一致），false=已被挤下线或 Token 无效
     */
    boolean validateToken(Long userId, String token);

    /**
     * 尝试获取登录分布式锁。
     * 防止同一账号在极短时间内发起多次登录请求导致会话状态错乱。
     *
     * @param userId 用户 ID
     * @return true=获取成功（可以继续登录流程），false=获取失败（有其他登录正在进行）
     */
    boolean tryAcquireLoginLock(Long userId);

    /**
     * 释放登录分布式锁。
     * 登录流程完成（无论成功或失败）后必须调用此方法释放锁。
     *
     * @param userId 用户 ID
     */
    void releaseLoginLock(Long userId);
}
