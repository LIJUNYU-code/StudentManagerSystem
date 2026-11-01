package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    /** 改密时间缓存：auth:pwdt:<userId>，短 TTL 兼顾"改密尽快生效"与"少查 MySQL" */
    private static final String PWD_TIME_PREFIX = "auth:pwdt:";
    private static final Duration PWD_TIME_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public TokenBlacklistService(StringRedisTemplate redisTemplate, JwtUtil jwtUtil, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
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

    /**
     * 改密强制下线（阶段8）：token 签发时间是否早于该用户最近一次改密时间。
     *
     * @param userId token 里的 sub（用户 id）
     * @param issuedAt token 的 iat（签发时刻）
     * @return true = 划线之前签发的旧票，应拒绝
     */
    public boolean isStaleByPasswordChange(Long userId, Instant issuedAt) {
        LocalDateTime pwdChangeTime = getPwdChangeTime(userId);
        if (pwdChangeTime == null) {
            return false; // 从未改密：不做 iat 比较
        }
        LocalDateTime iat = LocalDateTime.ofInstant(issuedAt, ZoneId.systemDefault());
        // 严格小于：改密后新签发的 token 其签发时刻必然晚于划线时刻。
        // 前提是两侧都到毫秒（V8 迁移把 pwd_change_time 提到 DATETIME(3)，
        // token 侧用 iatMs claim），否则"同一秒内登录再改密"的旧票会逃过检查。
        return iat.isBefore(pwdChangeTime);
    }

    /**
     * 改密成功后调用：主动失效该用户的划线时间缓存。
     *
     * 为什么必须做：改密请求自身也会过过滤器，过滤器会把"改密前的旧划线时间"
     * 写进缓存（TTL 60s）。若不主动删除，改密后的最长 60 秒内，旧 token 会拿着
     * 旧划线时间被误判为"新票"放行——"改密立即下线"就被缓存打穿了。
     * 删除后下一个请求缓存 miss，强制回源 DB 拿到新值。
     */
    public void invalidatePwdTimeCache(Long userId) {
        redisTemplate.delete(PWD_TIME_PREFIX + userId);
    }

    /**
     * 查用户最近改密时间：先查 Redis 缓存（TTL 60s），未命中回源 MySQL。
     * 缓存的意义：每个带 token 的请求都会走到这里，不能每次都打 MySQL；
     * 60 秒过期保证改密后最多 1 分钟内全网生效。
     */
    private LocalDateTime getPwdChangeTime(Long userId) {
        String key = PWD_TIME_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // 缓存约定：""（空串）= 该用户从未改密，避免缓存穿透
            return cached.isEmpty() ? null : LocalDateTime.parse(cached);
        }
        User user = userMapper.selectById(userId);
        LocalDateTime pwdChangeTime = (user == null) ? null : user.getPwdChangeTime();
        redisTemplate.opsForValue().set(key, pwdChangeTime == null ? "" : pwdChangeTime.toString(), PWD_TIME_TTL);
        return pwdChangeTime;
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
