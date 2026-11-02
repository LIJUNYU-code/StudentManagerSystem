package com.example.demo.service;

import com.example.demo.common.LocalExpiringCache;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
 * JWT 登出黑名单 + 改密划线（基于 Redis，带本地降级）。
 *
 * <h3>为什么要有降级</h3>
 * 这个类是每个带 token 请求的必经之路。Redis 一旦不可用，原来会直接把
 * {@code RedisConnectionFailureException} 抛到 Controller，客户端收到的是
 * 「401 未登录」——前端清除 token 跳登录页，登录成功后再发请求又 401，
 * 形成死循环。一个缓存服务挂掉不该让整个系统不可用。
 *
 * <h3>降级策略：放行 + 本地兜底</h3>
 * Redis 不可用时 {@link #isRevoked} 返回 false（放行），代价是「故障期间已登出的
 * token 仍可能有效」。这个取舍是明确的：
 * <ul>
 *   <li>拒绝一切请求 → 100% 用户不可用；</li>
 *   <li>放行 → 极少数已登出的 token 多活几分钟（token 本身 2 小时过期）。</li>
 * </ul>
 * 后者影响面小得多。同时 {@link #revoke} 在 Redis 失败时把 token 写进
 * <b>本地缓存</b>，所以「故障期间登出」在<b>本实例内</b>仍然立即生效。
 *
 * <h3>熔断</h3>
 * 只 try-catch 不够：Redis 连接超时配置是 2 秒，每个请求都等 2 秒再降级，
 * 系统会变成「可用但极慢」，比直接失败更难排查。所以失败后进入
 * {@link #FAILURE_COOLDOWN_MS} 冷却期，期间<b>直接跳过 Redis</b>，
 * 冷却结束后的第一个请求再试探一次（半开）。这是最朴素的熔断器。
 *
 * <h3>改密划线为什么不降级到本地缓存</h3>
 * 改密时间的权威来源是 MySQL（{@code user.pwd_change_time}），Redis 只是它前面的
 * 一层读缓存。所以 Redis 挂了直接回源 DB 即可——结果<b>完全正确</b>，只是每次请求
 * 多一条按主键的查询。这条路径不需要「放行」这种妥协。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "auth:bl:";
    /** 改密时间缓存：auth:pwdt:<userId>，短 TTL 兼顾"改密尽快生效"与"少查 MySQL" */
    private static final String PWD_TIME_PREFIX = "auth:pwdt:";
    private static final Duration PWD_TIME_TTL = Duration.ofSeconds(60);

    /** 熔断冷却时长：失败后这段时间内不再尝试 Redis，避免每个请求都等超时 */
    private static final long FAILURE_COOLDOWN_MS = 10_000L;

    /** 本地兜底缓存容量：黑名单 + 划线时间，1 万条对单机足够 */
    private static final int LOCAL_CACHE_MAX_SIZE = 10_000;

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /**
     * Redis 不可用时的本地兜底。
     * 黑名单：key = 与 Redis 相同的 {@code auth:bl:<digest>}；
     * 划线时间：key = {@code auth:pwdt:<userId>}，value 为空串表示"从未改密"。
     */
    private final LocalExpiringCache localCache = new LocalExpiringCache(LOCAL_CACHE_MAX_SIZE);

    /** 最近一次 Redis 失败的时刻；0 表示从未失败（或已恢复） */
    private volatile long lastFailureAt = 0L;

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

        String key = KEY_PREFIX + digest(token);

        // 无论 Redis 是否可用，先写本地：保证「故障期间登出」在本实例内立即生效。
        // 正常时这一份是多余的（Redis 命中就返回了），但它让降级路径的行为一致。
        localCache.put(key, "1", ttlMillis);

        if (redisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
                markRecovered();
                return;
            } catch (DataAccessException e) {
                markFailure("revoke", e);
            }
        }
        log.warn("Redis 不可用，登出黑名单仅写入本地缓存（本实例生效，多实例部署时其它实例看不到）");
    }

    /** 过滤器调用：token 是否已被登出。Redis 不可用时查本地缓存（放行优先）。 */
    public boolean isRevoked(String token) {
        String key = KEY_PREFIX + digest(token);

        if (redisAvailable()) {
            try {
                boolean revoked = Boolean.TRUE.equals(redisTemplate.hasKey(key));
                markRecovered();
                return revoked;
            } catch (DataAccessException e) {
                markFailure("isRevoked", e);
            }
        }
        // 降级：本地缓存里有就拒绝（覆盖"故障期间登出"），没有就放行
        return localCache.get(key) != null;
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
     *
     * <p><b>注意：本方法绝不能向外抛异常。</b> 它被 {@code UserServiceImpl.changePassword}
     * 在 {@code @Transactional} 事务内调用，抛异常会让「密码已改」整体回滚——
     * 用户看到的是"改密失败"，但更糟的是缓存也没清掉，状态变得难以解释。
     * 所以这里对 Redis 异常一律吞掉，只记日志。
     */
    public void invalidatePwdTimeCache(Long userId) {
        String key = PWD_TIME_PREFIX + userId;
        // 本地兜底必须清，否则 Redis 故障期间本地缓存会一直返回旧划线时间
        localCache.remove(key);

        if (!redisAvailable()) {
            log.warn("Redis 不可用，划线缓存仅清理了本地副本；Redis 恢复后旧值最多再存活 {} 秒", PWD_TIME_TTL.toSeconds());
            return;
        }
        try {
            redisTemplate.delete(key);
            markRecovered();
        } catch (DataAccessException e) {
            // 故意吞掉：不能让缓存清理失败拖垮改密事务（见方法注释）
            markFailure("invalidatePwdTimeCache", e);
            log.warn("清理划线缓存失败（已忽略，不影响改密本身）", e);
        }
    }

    /**
     * 查用户最近改密时间：先查 Redis 缓存（TTL 60s），未命中回源 MySQL。
     * 缓存的意义：每个带 token 的请求都会走到这里，不能每次都打 MySQL；
     * 60 秒过期保证改密后最多 1 分钟内全网生效。
     *
     * <p>Redis 不可用时直接查 MySQL——DB 是权威来源，这条路结果完全正确，
     * 只是多一次主键查询。这条不需要"放行"妥协。
     */
    private LocalDateTime getPwdChangeTime(Long userId) {
        String key = PWD_TIME_PREFIX + userId;

        if (redisAvailable()) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                markRecovered();
                if (cached != null) {
                    // 缓存约定：""（空串）= 该用户从未改密，避免缓存穿透
                    return cached.isEmpty() ? null : LocalDateTime.parse(cached);
                }
            } catch (DataAccessException e) {
                markFailure("getPwdChangeTime", e);
                // 落到下面的直查 DB
            }
        } else {
            // 熔断期间：本地缓存可能存有故障前的值，先看它
            String local = localCache.get(key);
            if (local != null) {
                return local.isEmpty() ? null : LocalDateTime.parse(local);
            }
        }

        // 权威来源：MySQL。Redis 不可用时这是唯一正确路径，不是"降级凑合"
        User user = userMapper.selectById(userId);
        LocalDateTime pwdChangeTime = (user == null) ? null : user.getPwdChangeTime();
        String serialized = (pwdChangeTime == null) ? "" : pwdChangeTime.toString();

        localCache.put(key, serialized, PWD_TIME_TTL.toMillis());
        if (redisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, serialized, PWD_TIME_TTL);
                markRecovered();
            } catch (DataAccessException e) {
                markFailure("getPwdChangeTime(cache)", e);
            }
        }
        return pwdChangeTime;
    }

    // ==================== 熔断器 ====================

    /**
     * 是否该尝试 Redis。
     * <p>
     * 从未失败 → 试；失败后在冷却期内 → 跳过（直接走降级）；
     * 冷却期已过 → 允许试一次（半开状态，成功即恢复，失败则重新计时）。
     */
    private boolean redisAvailable() {
        long last = lastFailureAt;
        return last == 0L || (System.currentTimeMillis() - last) >= FAILURE_COOLDOWN_MS;
    }

    /** 记录失败：只有第一次进入故障态时打日志，避免冷却期内刷屏。 */
    private void markFailure(String operation, Exception e) {
        boolean wasHealthy = lastFailureAt == 0L;
        lastFailureAt = System.currentTimeMillis();
        if (wasHealthy) {
            log.warn("Redis 操作失败（{}），进入 {} 秒降级窗口：{}", operation,
                    FAILURE_COOLDOWN_MS / 1000, e.getMessage());
        }
    }

    /** 成功一次即认为恢复。 */
    private void markRecovered() {
        if (lastFailureAt != 0L) {
            lastFailureAt = 0L;
            log.info("Redis 已恢复，退出降级模式");
        }
    }

    /** 当前是否处于降级状态（供健康检查/测试使用）。 */
    public boolean isDegraded() {
        return !redisAvailable();
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
