package com.example.demo.config;


import com.example.demo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // TODO 1：从请求头读取 Authorization
        String authorization = request.getHeader("Authorization");
        // TODO 2：判断是否以 "Bearer " 开头
        if( authorization == null || (!authorization.startsWith("Bearer "))){
            filterChain.doFilter(request,response);
            return;
        }

        // TODO 3：提取 Token，调用 jwtUtil.parseToken(token)
        String token = authorization.substring(7);

        try {
            Long id = jwtUtil.parseToken(token);

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                            id,
                            null,
                            java.util.Collections.emptyList()
                    );

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