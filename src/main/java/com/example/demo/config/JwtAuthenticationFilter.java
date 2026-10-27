package com.example.demo.config;


import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        //  1：从请求头读取 Authorization
        String authorization = request.getHeader("Authorization");
        //  2：判断是否以 "Bearer " 开头
        if( authorization == null || (!authorization.startsWith("Bearer "))){
            filterChain.doFilter(request,response);
            return;
        }

        //  3：提取 Token，调用 jwtUtil.parseToken(token)
        String token = authorization.substring(7);


        try {
            Claims claims = jwtUtil.parseToken(token);

            // 阶段7：登出黑名单检查——签名有效但已被登出的 token 一律拒绝。
            // 注意顺序：先查黑名单再信任 claims，被登出的 token 不产生任何认证信息。
            if (tokenBlacklistService.isRevoked(token)) {
                SecurityContextHolder.clearContext();
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "登录已失效，请重新登录"
                );
                return;
            }

            Long id = Long.valueOf(claims.getSubject());

            String role = claims.get("role", String.class);
            List<SimpleGrantedAuthority> authorities = (role == null)
                    ? Collections.emptyList()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(id, null, authorities);
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException e) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "无效或已过期的 Token"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}