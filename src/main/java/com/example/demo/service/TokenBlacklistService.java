package com.example.demo.service;

import com.example.demo.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 登出黑名单（基于 Redis，阶段7）。
 *
 * 原理：JWT 无状态，签发后服务器无法"作废"它。登出时把 token 摘要写入
 * Redis 并设置 TTL = token 剩余有效期；过滤器每次请求检查黑名单，
 * 命中即拒绝。TTL 到期后 Redis 自动删除条目，无需任何清理代码。
 *
 * key 设计：auth:bl:<sha256(token)> —— 前缀做命名空间隔离，
 * 摘要替代原 token（key 从几百字节缩到 64 字符，省内存且查找更快）。
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:bl:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(StringRedisTemplate redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    /** 登出时调用：把 token 加入黑名单，TTL = 距过期的剩余时间。 */
    public void revoke(String token) {
        long ttlMillis;
        try {
            ttlMillis = jwtUtil.parseToken(token).getExpiration().getTime()
                    - System.currentTimeMillis();
        } catch (Exception e) {
            // token 无效/已过期：过滤器本来就会拒绝，无需入黑名单
            return;
        }
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + digest(token), "1", Duration.ofMillis(ttlMillis));
    }

    /** 过滤器调用：token 是否已被登出。 */
    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + digest(token)));
    }

    /** SHA-256 摘要（Hex 字符串）。 */
    private String digest(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JVM 必带 SHA-256，理论不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
