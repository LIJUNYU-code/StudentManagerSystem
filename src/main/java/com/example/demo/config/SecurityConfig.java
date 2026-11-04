package com.example.demo.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                // 当前使用 JWT 的无状态 API 方案
                .csrf(AbstractHttpConfigurer::disable)

                // 不使用服务器 Session 保存登录状态
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 配置哪些接口允许匿名访问
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/student/login",
                                "/student/register",
                                "/auth/register",
                                "/auth/login",
                                // 接口文档（springdoc）：这三个路径必须匿名放行，
                                // 否则想看文档还得先拿 token，等于自己把自己锁在门外。
                                // 生产环境如不希望公开，可用 springdoc.api-docs.enabled=false 关掉。
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // 未认证（无/无效 token）返回 401，而不是默认的 403，
                // 语义更准确：401=没登录，403=登录了但权限不够
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
                        }).accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"code\":403,\"message\":\"没有权限执行此操作\",\"data\":null}");
                        })
                )

                // 不使用默认表单登录和 HTTP Basic 登录
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }
}