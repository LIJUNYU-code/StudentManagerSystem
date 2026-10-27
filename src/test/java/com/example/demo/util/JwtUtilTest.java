package com.example.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：不启动 Spring、不连数据库，纯 JVM 内验证 JWT 契约。
 * AAA 结构：Arrange（准备）→ Act（调用）→ Assert（断言）。
 */
class JwtUtilTest {

    private static final String SECRET =
            "Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=";
    private static final long EXPIRATION_MS = 7_200_000L; // 与 application.yaml 的 7200000 一致

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // Arrange：JwtUtil 的 secret/expiration 靠 @Value 注入，
        // 单元测试没有 Spring 容器，用反射测试工具手动注入等价值
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
    }

    @Test
    @DisplayName("生成后解析：subject/name/role 三个字段完整往返")
    void generateThenParse_roundTrip() {
        // Act
        String token = jwtUtil.generateToken(7L, "张老师", "teacher");
        Claims claims = jwtUtil.parseToken(token);

        // Assert
        assertEquals("7", claims.getSubject());
        assertEquals("张老师", claims.get("name", String.class));
        assertEquals("teacher", claims.get("role", String.class));
    }

    @Test
    @DisplayName("过期时间 = 签发时间 + expiration（允许 5 秒误差）")
    void expiration_isNowPlusConfigured() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateToken(1L, "a", "admin");
        long after = System.currentTimeMillis();

        Claims claims = jwtUtil.parseToken(token);
        long exp = claims.getExpiration().getTime();

        // exp 必落在 [before, after] + EXPIRATION 区间内
        assertTrue(exp >= before + EXPIRATION_MS - 1000, "过期时间过早");
        assertTrue(exp <= after + EXPIRATION_MS + 1000, "过期时间过晚");
    }

    @Test
    @DisplayName("篡改 token 的任何字符，解析必须抛 JwtException")
    void tamperedToken_rejected() {
        String token = jwtUtil.generateToken(7L, "张老师", "teacher");

        // 篡改 payload 中间一段字符，制造"伪签名"
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(tampered),
                "被篡改的 token 不应通过验签");
    }

    @Test
    @DisplayName("错误密钥签的 token 被正确密钥的解析方拒绝")
    void wrongSecretToken_rejected() {
        // Arrange：另一个用不同密钥的 JwtUtil（模拟攻击者自签 token）
        JwtUtil attacker = new JwtUtil();
        ReflectionTestUtils.setField(attacker, "secret",
                "YW5vdGhlci1kZXZvbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci10ZXN0LWhvb2s=");
        ReflectionTestUtils.setField(attacker, "expiration", EXPIRATION_MS);

        // Act + Assert
        String forged = attacker.generateToken(1L, "hacker", "admin");
        assertThrows(JwtException.class, () -> jwtUtil.parseToken(forged),
                "他人密钥签发的 token 不应通过验签");
    }

    @Test
    @DisplayName("过期 token 被拒绝（把 expiration 设为 0，token 即刻过期）")
    void expiredToken_rejected() {
        JwtUtil expiredUtil = new JwtUtil();
        ReflectionTestUtils.setField(expiredUtil, "secret", SECRET);
        ReflectionTestUtils.setField(expiredUtil, "expiration", 0L);

        String token = expiredUtil.generateToken(1L, "a", "teacher");
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwtUtil.parseToken(token),
                "已过期的 token 不应通过解析");
    }
}
