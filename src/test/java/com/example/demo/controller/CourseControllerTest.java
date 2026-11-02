package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.CourseService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.CourseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CourseController 切片测试。
 * <p>
 * 额外关注：课程分页参数叫 <b>pageSize</b> 而不是 size（和 Clazz/Student 不一致），
 * 这是历史遗留的不一致。测试固定住当前行为，将来统一命名时会立刻发现影响面。
 */
@WebMvcTest(controllers = CourseController.class)
@ContextConfiguration(classes = CourseControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class CourseControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long COURSE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CourseService courseService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void allowTokensByDefault() {
        when(tokenBlacklistService.isRevoked(any())).thenReturn(false);
        when(tokenBlacklistService.isStaleByPasswordChange(anyLong(), any(Instant.class))).thenReturn(false);
    }

    private String token(long userId, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, "测试用户", role);
    }

    private CourseResponse sampleCourse() {
        return CourseResponse.builder()
                .id(COURSE_ID).name("数据结构").credit(new BigDecimal("3.0"))
                .teacherId(TEACHER_ID).build();
    }

    // ==================== 读 ====================

    @Test
    @DisplayName("学生能查课程列表（选课页面要用）")
    void student_canList() throws Exception {
        when(courseService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/course").header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("分页参数用 pageSize：传 pageSize=5 能正常绑定")
    void list_acceptsPageSize() throws Exception {
        when(courseService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 5L));

        mockMvc.perform(get("/course?page=1&pageSize=5")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk());

        verify(courseService).page(org.mockito.ArgumentMatchers.argThat(
                q -> q != null && Integer.valueOf(5).equals(q.getPageSize())));
    }

    @Test
    @DisplayName("pageSize 超过上限 100 → 400")
    void list_pageSizeTooLarge() throws Exception {
        mockMvc.perform(get("/course?pageSize=1000")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("查课程详情 → 200")
    void query_success() throws Exception {
        when(courseService.query(COURSE_ID)).thenReturn(sampleCourse());

        mockMvc.perform(get("/course/" + COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("数据结构"))
                .andExpect(jsonPath("$.data.credit").value(3.0));
    }

    // ==================== 写 ====================

    @Test
    @DisplayName("学生新建课程 → 403")
    void student_cannotAdd() throws Exception {
        mockMvc.perform(post("/course")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据结构\",\"credit\":3.0}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("教师新建课程 → 200")
    void teacher_canAdd() throws Exception {
        when(courseService.add(any())).thenReturn(sampleCourse());

        mockMvc.perform(post("/course")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据结构\",\"credit\":3.0}"))
                .andExpect(status().isOk());

        verify(courseService).add(any());
    }

    @Test
    @DisplayName("学分超出范围（11）→ 400，且不调用 Service")
    void add_creditOutOfRange() throws Exception {
        mockMvc.perform(post("/course")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据结构\",\"credit\":11}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("学生改课程 → 403")
    void student_cannotUpdate() throws Exception {
        mockMvc.perform(put("/course/" + COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credit\":4.0}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("教师改课程 → 200")
    void teacher_canUpdate() throws Exception {
        when(courseService.update(eq(COURSE_ID), any())).thenReturn(sampleCourse());

        mockMvc.perform(put("/course/" + COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credit\":4.0}"))
                .andExpect(status().isOk());

        verify(courseService).update(eq(COURSE_ID), any());
    }

    @Test
    @DisplayName("学生删课程 → 403")
    void student_cannotDelete() throws Exception {
        mockMvc.perform(delete("/course/" + COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courseService);
    }

    @Test
    @DisplayName("删除已有选课的课程 → 409（业务保护透传到 HTTP）")
    void delete_hasEnrollments() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "该课程已有 8 条选课记录，请先清空选课再删除"))
                .when(courseService).delete(COURSE_ID);

        mockMvc.perform(delete("/course/" + COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("8 条选课")));
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            CourseController.class
    })
    static class SliceConfig {
        @Bean
        JwtUtil jwtUtil() {
            JwtUtil util = new JwtUtil();
            ReflectionTestUtils.setField(util, "secret",
                    "Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=");
            ReflectionTestUtils.setField(util, "expiration", 7200000L);
            return util;
        }
    }
}
