package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（OpenAPI / Swagger UI）的元信息与认证方案配置。
 *
 * <h2>文档地址</h2>
 * <ul>
 *   <li>可视界面：{@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>原始 JSON：{@code http://localhost:8080/v3/api-docs}（可导入 Apifox / Postman）</li>
 * </ul>
 * 这两个路径已在 {@code SecurityConfig} 里 permitAll，否则"看文档要先登录"就自相矛盾了。
 *
 * <h2>为什么这里要配 bearerAuth</h2>
 * 本项目除了登录/注册之外的接口都要求 {@code Authorization: Bearer <token>}。
 * 如果不在文档里声明这个安全方案，Swagger UI 上就<b>没有「Authorize」按钮</b>，
 * 每个受保护接口都只能手工去别处拼请求头，失去了"在文档里直接试"的意义。
 * <p>
 * 注册之后，点右上角 Authorize，只需粘贴 <b>token 本身</b>（不用带 "Bearer " 前缀，
 * Swagger UI 会自动补上），之后所有请求都会自动携带。
 *
 * <h2>和 @PreAuthorize 的关系</h2>
 * 这里的 {@code SecurityRequirement} 只表示「这些接口需要认证」，是<b>给人和工具看的描述</b>，
 * 不做任何拦截；真正拦请求的是 Spring Security 的过滤链和 Controller 上的
 * {@code @PreAuthorize}。文档写得再全，也代替不了那两处代码——这点必须分清。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI classHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClassHub 学生管理系统 API")
                        .version("v1.0")
                        .description("""
                                学生-班级-课程-成绩-考勤一体化管理平台的后端接口文档。

                                **角色说明**
                                - `ADMIN` 管理员：用户审核、全局管理
                                - `TEACHER` 教师：班级/课程/成绩/考勤/请假审批
                                - `STUDENT` 学生：查自己的成绩、考勤、请假

                                **如何使用**
                                1. 先调用 `POST /auth/login`（教师/管理员）或 `POST /student/login`（学生）拿到 `token`
                                2. 点右上角 **Authorize**，粘贴 token（不要带 "Bearer " 前缀）
                                3. 之后即可在本页直接调用受保护接口

                                **通用约定**
                                - 统一响应体：`{ "code": 200, "message": "success", "data": ... }`
                                - 分页响应：`data` 内含 `total` / `records` / `page` / `size`
                                - `401` 未登录或 token 失效；`403` 已登录但角色不够；`404` 资源不存在；`409` 唯一冲突；`400` 参数不合法
                                """)
                        .contact(new Contact().name("JunyuLi"))
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token 原文（不带 Bearer 前缀）")))
                // 全局声明：默认所有接口都可能需要认证。
                // 登录/注册等公开接口在 Controller 上用 @SecurityRequirements（复数）单独标注为无需认证。
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
