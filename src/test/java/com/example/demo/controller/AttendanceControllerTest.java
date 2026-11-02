package com.example.demo.controller;

import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AttendanceController 切片测试。
 * <p>
 * 除了常规的角色矩阵，这里重点验证两件"只在 HTTP 层才暴露"的事：
 * <ol>
 *   <li><b>嵌套校验是否真的生效</b>：AttendanceBatchRequestDTO.items 是 List，
 *       如果漏了 @Valid，列表内每个 Item 的 @NotNull/@Max 会静默失效，
 *       非法状态值能直接穿过 Controller 进 Service。这条只能靠 HTTP 层测。</li>
 *   <li><b>日期格式错误走 400 而不是 500</b>：LocalDate 绑定失败抛的是
 *       MethodArgumentTypeMismatchException，要确认全局处理器把它翻译成 400。</li>
 * </ol>
 */
@WebMvcTest(controllers = AttendanceController.class)
@ContextConfiguration(classes = AttendanceControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class AttendanceControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long COURSE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AttendanceService attendanceService;

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

    private static final String VALID_BATCH = """
            {
              "courseId": 5,
              "attendDate": "2026-11-02",
              "sessionNo": 1,
              "items": [
                {"studentId": 1, "status": 0, "remark": null},
                {"studentId": 2, "status": 4, "remark": "无故缺席"}
              ]
            }
            """;

    // ==================== POST /attendance/batch ====================

    @Test
    @DisplayName("教师批量点名 → 200，返回影响条数")
    void teacher_canBatch() throws Exception {
        when(attendanceService.batchSave(any())).thenReturn(2);

        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    @DisplayName("管理员也能批量点名 → 200")
    void admin_canBatch() throws Exception {
        when(attendanceService.batchSave(any())).thenReturn(2);

        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(9L, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生批量点名 → 403，且不调用 Service")
    void student_cannotBatch() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("未登录批量点名 → 401")
    void anonymous_cannotBatch() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("嵌套校验生效：items[].status = 9 → 400（漏 @Valid 这条会漏过去）")
    void batch_nestedValidationOnItemStatus() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":5,"attendDate":"2026-11-02","sessionNo":1,
                                 "items":[{"studentId":1,"status":9}]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("嵌套校验生效：items[].studentId 为 null → 400")
    void batch_nestedValidationOnStudentId() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":5,"attendDate":"2026-11-02","sessionNo":1,
                                 "items":[{"status":0}]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("items 为空数组 → 400（@NotEmpty）")
    void batch_emptyItems() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":5,"attendDate":"2026-11-02","sessionNo":1,"items":[]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("sessionNo = 0 → 400（@Min(1)）")
    void batch_sessionNoTooSmall() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":5,"attendDate":"2026-11-02","sessionNo":0,
                                 "items":[{"studentId":1,"status":0}]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("缺 courseId → 400")
    void batch_missingCourseId() throws Exception {
        mockMvc.perform(post("/attendance/batch")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendDate":"2026-11-02","sessionNo":1,
                                 "items":[{"studentId":1,"status":0}]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    // ==================== GET /attendance ====================

    @Test
    @DisplayName("教师查点名名单 → 200，未点名学生 status 为 null")
    void teacher_canQueryRoster() throws Exception {
        when(attendanceService.roster(any())).thenReturn(List.of(
                new AttendanceStudentVO(1L, "S202500001", "张三", null, null, null),
                new AttendanceStudentVO(2L, "S202500002", "李四", 88L, 4, "无故缺席")));

        mockMvc.perform(get("/attendance")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .param("attendDate", "2026-11-02")
                        .param("sessionNo", "1")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 未点名的那条：attendanceId 和 status 都是 null，前端据此显示"未点名"
                .andExpect(jsonPath("$.data[0].attendanceId").doesNotExist())
                .andExpect(jsonPath("$.data[1].status").value(4));
    }

    @Test
    @DisplayName("非法日期格式 → 400（不是 500）")
    void roster_badDateFormat() throws Exception {
        mockMvc.perform(get("/attendance")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .param("attendDate", "not-a-date")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("缺 attendDate → 400")
    void roster_missingDate() throws Exception {
        mockMvc.perform(get("/attendance")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("学生查点名名单 → 403")
    void student_cannotQueryRoster() throws Exception {
        mockMvc.perform(get("/attendance")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .param("attendDate", "2026-11-02")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attendanceService);
    }

    // ==================== PUT /attendance/{id} ====================

    @Test
    @DisplayName("教师修改单条考勤 → 200")
    void teacher_canUpdate() throws Exception {
        mockMvc.perform(put("/attendance/{id}", 88L)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3,\"remark\":\"病假\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("修改时 status 越界 → 400")
    void update_statusOutOfRange() throws Exception {
        mockMvc.perform(put("/attendance/{id}", 88L)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":7}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("修改时缺 status → 400")
    void update_missingStatus() throws Exception {
        mockMvc.perform(put("/attendance/{id}", 88L)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"病假\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("学生修改考勤 → 403")
    void student_cannotUpdate() throws Exception {
        mockMvc.perform(put("/attendance/{id}", 88L)
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attendanceService);
    }

    // ==================== GET /attendance/stats ====================

    @Test
    @DisplayName("教师查出勤统计 → 200")
    void teacher_canQueryStats() throws Exception {
        AttendanceStatVO vo = new AttendanceStatVO();
        vo.setStudentId(1L);
        vo.setStudentNo("S202500001");
        vo.setStudentName("张三");
        vo.setTotal(10L);
        vo.setPresent(8L);
        vo.setLate(1L);
        vo.setEarlyLeave(0L);
        vo.setLeaveCount(1L);
        vo.setAbsent(0L);
        vo.setAttendanceRate(new BigDecimal("0.8000"));
        when(attendanceService.stats(COURSE_ID)).thenReturn(List.of(vo));

        mockMvc.perform(get("/attendance/stats")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentName").value("张三"))
                .andExpect(jsonPath("$.data[0].present").value(8))
                .andExpect(jsonPath("$.data[0].attendanceRate").value(0.8));
    }

    @Test
    @DisplayName("缺 courseId 参数 → 400")
    void stats_missingCourseId() throws Exception {
        mockMvc.perform(get("/attendance/stats")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("学生查出勤统计 → 403")
    void student_cannotQueryStats() throws Exception {
        mockMvc.perform(get("/attendance/stats")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attendanceService);
    }

    // ==================== GET /attendance/my（只有学生） ====================

    @Test
    @DisplayName("学生查自己的考勤 → 200，带课程名")
    void student_canQueryMy() throws Exception {
        when(attendanceService.myAttendance()).thenReturn(List.of(
                AttendanceRecordVO.builder()
                        .id(3L).courseId(COURSE_ID).courseName("数据结构")
                        .attendDate(LocalDate.of(2026, 11, 2)).sessionNo(1)
                        .status(0).remark(null).build()));

        mockMvc.perform(get("/attendance/my")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value("数据结构"))
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andExpect(jsonPath("$.data[0].attendDate").value("2026-11-02"));
    }

    @Test
    @DisplayName("教师访问 /attendance/my → 403（教师没有「自己的考勤」）")
    void teacher_cannotQueryMy() throws Exception {
        mockMvc.perform(get("/attendance/my")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("未登录访问 /attendance/my → 401")
    void anonymous_cannotQueryMy() throws Exception {
        mockMvc.perform(get("/attendance/my"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            AttendanceController.class
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
