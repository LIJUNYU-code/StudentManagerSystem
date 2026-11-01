package com.example.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // 获取签名密钥
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 生成 JWT
    public String generateToken(Long userId, String name, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("name", name)
                .claim("role", role)
                // iat 是 JWT 标准声明，只精确到秒（RFC 7519 规定 NumericDate 为秒）。
                // 改密下线要靠"签发时刻 vs 改密时刻"比较，秒级精度会让
                // "同一秒内登录再改密"的旧票逃过检查，所以另带一个毫秒 claim。
                .claim("iatMs", now.getTime())
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    // 验证并解析 Token
    public Claims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims;
    }

    /**
     * 取 token 的签发时刻（毫秒精度）。
     *
     * 优先用自定义 claim iatMs；老 token（升级前签发）没有该 claim，
     * 退回到标准 iat（秒级）—— 这类 token 本来也活不过 2 小时。
     */
    public java.time.Instant getIssuedAtMillis(Claims claims) {
        Object iatMs = claims.get("iatMs");
        if (iatMs instanceof Number n) {
            return java.time.Instant.ofEpochMilli(n.longValue());
        }
        return claims.getIssuedAt().toInstant();
    }
}
