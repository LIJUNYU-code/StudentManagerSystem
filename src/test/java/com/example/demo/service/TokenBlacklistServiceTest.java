package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TokenBlacklistService} 单测：重点验证「Redis 挂掉时系统仍然可用」这条降级契约。
 *
 * <p>为什么这组测试值得单独写：降级逻辑最容易在后续重构中被"顺手清理"掉，
 * 而它出问题时的表现（全站 401 死循环）在单元测试阶段之外很难被察觉。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserMapper userMapper;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TokenBlacklistService(redisTemplate, jwtUtil, userMapper);
    }

    /** 让 jwtUtil.parseToken 返回一个「还有 1 小时才过期」的合法 token */
    private void mockValidToken(String token) {
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 3_600_000L));
        when(jwtUtil.parseToken(token)).thenReturn(claims);
    }

    // ==================== revoke ====================

    @Test
    @DisplayName("revoke：正常时同时写本地与 Redis")
    void revoke_writesLocalAndRedis() {
        mockValidToken("t1");

        service.revoke("t1");

        verify(valueOps).set(anyString(), eq("1"), any(Duration.class));
        assertFalse(service.isDegraded());
    }

    @Test
    @DisplayName("revoke：token 无法解析时静默忽略，不写缓存")
    void revoke_invalidToken_ignored() {
        when(jwtUtil.parseToken("bad")).thenThrow(new IllegalArgumentException("bad token"));

        service.revoke("bad");

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("revoke：token 已过期时忽略（过滤器本来就会拒绝）")
    void revoke_expiredToken_ignored() {
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 1000L));
        when(jwtUtil.parseToken("old")).thenReturn(claims);

        service.revoke("old");

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("revoke：Redis 抛异常时不向上抛，且登出在本实例内仍生效")
    void revoke_redisDown_stillRevokesLocally() {
        mockValidToken("t2");
        // set 返回 void，不能用 when(...) 打桩，必须用 doThrow().when(...)
        doThrow(new RedisConnectionFailureException("Unable to connect to Redis"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertDoesNotThrow(() -> service.revoke("t2"));

        // 关键：Redis 挂了，但「刚登出的 token」必须在本实例内立刻失效
        assertTrue(service.isRevoked("t2"), "降级期间本地黑名单应生效");
    }

    // ==================== isRevoked ====================

    @Test
    @DisplayName("isRevoked：Redis 命中则返回 true")
    void isRevoked_redisHit() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertTrue(service.isRevoked("t3"));
    }

    @Test
    @DisplayName("isRevoked：Redis 未命中则返回 false")
    void isRevoked_redisMiss() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertFalse(service.isRevoked("t4"));
    }

    @Test
    @DisplayName("isRevoked：Redis 挂且本地无记录 → 放行（而不是拒绝一切请求）")
    void isRevoked_redisDown_localMiss_allows() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertFalse(service.isRevoked("t5"), "降级策略是放行，不能把用户全挡在门外");
        assertTrue(service.isDegraded());
    }

    // ==================== 熔断 ====================

    @Test
    @DisplayName("熔断：失败后冷却期内不再访问 Redis，避免每个请求都等 2 秒超时")
    void circuitBreaker_skipsRedisDuringCooldown() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        service.isRevoked("a");   // 第 1 次：真的去连，失败
        service.isRevoked("b");   // 第 2 次：冷却期内，应直接走本地
        service.isRevoked("c");

        verify(redisTemplate, times(1)).hasKey(anyString());
    }

    @Test
    @DisplayName("熔断：冷却期结束后重新试探 Redis（半开）")
    void circuitBreaker_retriesAfterCooldown() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        service.isRevoked("a");
        verify(redisTemplate, times(1)).hasKey(anyString());

        // 把上次失败时刻往前拨 11 秒，模拟冷却期已过
        ReflectionTestUtils.setField(service, "lastFailureAt",
                System.currentTimeMillis() - 11_000L);

        // 注意：原来的 stub 是「抛异常」，此时用 when(mock.hasKey(...)) 重新打桩
        // 会真的调用一次方法而把异常抛出来。覆盖会抛异常的 stub 必须用 doReturn。
        doReturn(false).when(redisTemplate).hasKey(anyString());
        assertFalse(service.isRevoked("b"));
        verify(redisTemplate, times(2)).hasKey(anyString());
    }

    @Test
    @DisplayName("熔断：一旦成功即恢复，isDegraded 回到 false")
    void circuitBreaker_recoversOnSuccess() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        service.isRevoked("a");
        assertTrue(service.isDegraded());

        ReflectionTestUtils.setField(service, "lastFailureAt",
                System.currentTimeMillis() - 11_000L);
        doReturn(false).when(redisTemplate).hasKey(anyString());
        service.isRevoked("b");

        assertFalse(service.isDegraded());
    }

    // ==================== isStaleByPasswordChange ====================

    @Test
    @DisplayName("改密划线：用户从未改密 → 不拦截")
    void stale_neverChangedPassword() {
        when(valueOps.get(anyString())).thenReturn("");
        User user = new User();
        user.setId(1L);
        user.setPwdChangeTime(null);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertFalse(service.isStaleByPasswordChange(1L, Instant.now()));
    }

    @Test
    @DisplayName("改密划线：token 签发早于改密时间 → 拦截")
    void stale_tokenIssuedBeforeChange() {
        LocalDateTime pwdChangeTime = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
        when(valueOps.get(anyString())).thenReturn(pwdChangeTime.toString());

        Instant issuedAt = pwdChangeTime.minusMinutes(5)
                .atZone(ZoneId.systemDefault()).toInstant();

        assertTrue(service.isStaleByPasswordChange(2L, issuedAt));
    }

    @Test
    @DisplayName("改密划线：token 签发晚于改密时间 → 放行（改密后新签发的票）")
    void stale_tokenIssuedAfterChange() {
        LocalDateTime pwdChangeTime = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
        when(valueOps.get(anyString())).thenReturn(pwdChangeTime.toString());

        Instant issuedAt = pwdChangeTime.plusMinutes(5)
                .atZone(ZoneId.systemDefault()).toInstant();

        assertFalse(service.isStaleByPasswordChange(3L, issuedAt));
    }

    @Test
    @DisplayName("改密划线：Redis 挂时回源 MySQL，结果依然正确（不是凑合）")
    void stale_redisDown_fallsBackToDatabase() {
        when(valueOps.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        LocalDateTime pwdChangeTime = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
        User user = new User();
        user.setId(4L);
        user.setPwdChangeTime(pwdChangeTime);
        when(userMapper.selectById(4L)).thenReturn(user);

        Instant issuedAt = pwdChangeTime.minusMinutes(1)
                .atZone(ZoneId.systemDefault()).toInstant();

        assertTrue(service.isStaleByPasswordChange(4L, issuedAt),
                "Redis 不可用时改密划线仍须准确");
        verify(userMapper).selectById(4L);
    }

    // ==================== invalidatePwdTimeCache ====================

    @Test
    @DisplayName("清缓存：Redis 正常时删除对应 key")
    void invalidate_deletesRedisKey() {
        service.invalidatePwdTimeCache(5L);

        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("清缓存：Redis 抛异常时绝不向外抛——否则会把 @Transactional 的改密整体回滚")
    void invalidate_neverThrows() {
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertDoesNotThrow(() -> service.invalidatePwdTimeCache(6L));
    }

    @Test
    @DisplayName("清缓存：Redis 已处于熔断态时直接跳过，不发请求")
    void invalidate_skipsWhenDegraded() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        service.isRevoked("x"); // 触发熔断

        service.invalidatePwdTimeCache(7L);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("初始状态不是降级")
    void initiallyHealthy() {
        assertFalse(service.isDegraded());
        verify(userMapper, never()).selectById(anyLong());
    }
}
