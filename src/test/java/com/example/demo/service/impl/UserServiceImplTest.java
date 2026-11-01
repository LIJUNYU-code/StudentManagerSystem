package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.ChangePasswordRequestDTO;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.AdminUserResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试。
 * <p>
 * 这个类管的是"谁能进系统"，比业务模块更敏感：
 * 注册必须落到待审核、登录必须按状态三档拦截、禁用必须防自伤。
 * 这些规则一旦被改坏，症状不是报错而是"悄悄放行"——最难发现的那一类，
 * 所以每条规则都配一个用例钉住。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper, passwordEncoder, jwtUtil, tokenBlacklistService);
    }

    /** 清掉线程里的登录态，否则 disable 的"自操作"判断会读到上一个用例的残留。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 在 SecurityContext 里伪装成已登录的某个用户（过滤器真实做的事）。 */
    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private User user(Long id, String username, Integer status) {
        return User.builder()
                .id(id)
                .username(username)
                .password("$2a$10$encodedOld")
                .realName("张三")
                .role("teacher")
                .status(status)
                .build();
    }

    private UserRegisterRequestDTO registerRequest() {
        UserRegisterRequestDTO dto = new UserRegisterRequestDTO();
        dto.setUsername("newteacher");
        dto.setPassword("Passw0rd");
        dto.setRealName("李四");
        return dto;
    }

    private UserLoginRequestDTO loginRequest(String username, String password) {
        UserLoginRequestDTO dto = new UserLoginRequestDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    // ---------------- register ----------------

    @Test
    @DisplayName("注册：用户名已存在 → 409，且不插入任何记录")
    void register_duplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(registerRequest()));

        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("用户名已被占用"));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册：成功 → 密码加密入库，角色固定 teacher，状态固定 0（待审核）")
    void register_success() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("$2a$10$encodedNew");

        service.register(registerRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        assertEquals("newteacher", saved.getUsername());
        assertEquals("$2a$10$encodedNew", saved.getPassword());
        assertEquals("李四", saved.getRealName());
        // 这两条是业务规则，不能靠数据库列默认值——换库就悄悄变 null
        assertEquals("teacher", saved.getRole());
        assertEquals(0, saved.getStatus());
    }

    @Test
    @DisplayName("注册：明文密码绝不能出现在入库对象里")
    void register_passwordMustBeEncoded() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedNew");

        service.register(registerRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertTrue(captor.getValue().getPassword().startsWith("$2a$"));
    }

    // ---------------- login ----------------

    @Test
    @DisplayName("登录：用户不存在 → 401，且不签发 token")
    void login_userNotFound() {
        when(userMapper.selectByName("ghost")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("ghost", "whatever")));

        assertEquals(401, ex.getCode());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("登录：密码错误 → 401，且不签发 token")
    void login_wrongPassword() {
        when(userMapper.selectByName("teacher1")).thenReturn(user(1L, "teacher1", 1));
        when(passwordEncoder.matches("wrong", "$2a$10$encodedOld")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("teacher1", "wrong")));

        assertEquals(401, ex.getCode());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("登录：待审核（status=0）→ 403，提示待审核")
    void login_pendingReview() {
        when(userMapper.selectByName("teacher1")).thenReturn(user(1L, "teacher1", 0));
        when(passwordEncoder.matches("Passw0rd", "$2a$10$encodedOld")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("teacher1", "Passw0rd")));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("待审核"));
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("登录：已禁用（status=2）→ 403，提示已禁用")
    void login_disabled() {
        when(userMapper.selectByName("teacher1")).thenReturn(user(1L, "teacher1", 2));
        when(passwordEncoder.matches("Passw0rd", "$2a$10$encodedOld")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("teacher1", "Passw0rd")));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("登录：成功 → 返回 id/username/token")
    void login_success() {
        when(userMapper.selectByName("teacher1")).thenReturn(user(7L, "teacher1", 1));
        when(passwordEncoder.matches("Passw0rd", "$2a$10$encodedOld")).thenReturn(true);
        when(jwtUtil.generateToken(7L, "teacher1", "teacher")).thenReturn("jwt-token-abc");

        UserLoginResponseDTO resp = service.login(loginRequest("teacher1", "Passw0rd"));

        assertEquals(7L, resp.getId());
        assertEquals("teacher1", resp.getUsername());
        assertEquals("jwt-token-abc", resp.getToken());
    }

    // ---------------- changePassword ----------------

    @Test
    @DisplayName("改密：用户不存在 → 401")
    void changePassword_userNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(99L, changeRequest("old", "newpass")));

        assertEquals(401, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("改密：旧密码不对 → 400，且不更新、不失效缓存")
    void changePassword_wrongOldPassword() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 1));
        when(passwordEncoder.matches("bad-old", "$2a$10$encodedOld")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(1L, changeRequest("bad-old", "newpass")));

        assertEquals(400, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
        verify(tokenBlacklistService, never()).invalidatePwdTimeCache(any());
    }

    @Test
    @DisplayName("改密：新密码与旧密码相同 → 400（否则'改密下线'失去意义）")
    void changePassword_sameAsOld() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 1));
        // 两次 matches 都返回 true：旧密码对、新密码也等于旧密码
        when(passwordEncoder.matches(anyString(), eq("$2a$10$encodedOld"))).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(1L, changeRequest("oldpass", "oldpass")));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不能与当前密码相同"));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("改密：成功 → 写新密文、划下 pwdChangeTime、清缓存、返回新 token")
    void changePassword_success() {
        User existing = user(1L, "teacher1", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(passwordEncoder.matches("oldpass", "$2a$10$encodedOld")).thenReturn(true);
        when(passwordEncoder.matches("newpass", "$2a$10$encodedOld")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$10$encodedNew");
        when(jwtUtil.generateToken(1L, "teacher1", "teacher")).thenReturn("jwt-new");

        LocalDateTime before = LocalDateTime.now();
        UserLoginResponseDTO resp = service.changePassword(1L, changeRequest("oldpass", "newpass"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User saved = captor.getValue();

        assertEquals("$2a$10$encodedNew", saved.getPassword());
        // 划线时间是改密下线的命门：为 null 则旧 token 永远有效
        assertNotNull(saved.getPwdChangeTime());
        assertTrue(saved.getPwdChangeTime().isAfter(before.minusSeconds(1)));
        // 不清缓存，旧 token 还能靠 60 秒缓存逃过检查
        verify(tokenBlacklistService).invalidatePwdTimeCache(1L);
        assertEquals("jwt-new", resp.getToken());
    }

    private ChangePasswordRequestDTO changeRequest(String oldPassword, String newPassword) {
        ChangePasswordRequestDTO dto = new ChangePasswordRequestDTO();
        dto.setOldPassword(oldPassword);
        dto.setNewPassword(newPassword);
        return dto;
    }

    // ---------------- page ----------------

    @Test
    @DisplayName("审核列表：映射为 VO，且响应里不含 password")
    void page_mapsToVoWithoutPassword() {
        UserReviewQueryDTO query = new UserReviewQueryDTO();
        query.setPage(1);
        query.setPageSize(10);

        User u = user(3L, "teacher3", 0);
        u.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(u));
        page.setTotal(1);
        doReturn(page).when(userMapper).selectPage(any(), any());

        PageResult<AdminUserResponse> result = service.page(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        AdminUserResponse vo = result.getRecords().get(0);
        assertEquals(3L, vo.getId());
        assertEquals("teacher3", vo.getUsername());
        assertEquals(0, vo.getStatus());
    }

    @Test
    @DisplayName("审核列表：空结果不报错，返回 total=0")
    void page_empty() {
        UserReviewQueryDTO query = new UserReviewQueryDTO();
        query.setPage(1);
        query.setPageSize(10);

        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        doReturn(page).when(userMapper).selectPage(any(), any());

        PageResult<AdminUserResponse> result = service.page(query);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ---------------- approve ----------------

    @Test
    @DisplayName("审核：用户不存在 → 404")
    void approve_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(99L));

        assertEquals(404, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("审核：已不是待审核 → 409（重复点审核不该把人重置成正常）")
    void approve_notPending() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));

        assertEquals(409, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("审核：待审核 → 状态置 1")
    void approve_success() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 0));

        service.approve(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
    }

    // ---------------- disable ----------------

    @Test
    @DisplayName("禁用：用户不存在 → 404")
    void disable_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.disable(99L));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("禁用：待审核用户 → 409（该走审核流程，不是禁用）")
    void disable_pendingUser() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 0));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.disable(1L));

        assertEquals(409, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("禁用：已禁用 → 409，避免无意义重复操作")
    void disable_alreadyDisabled() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher1", 2));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.disable(1L));

        assertEquals(409, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("禁用：不能禁用自己 → 409（防管理员把自己锁在门外）")
    void disable_self() {
        when(userMapper.selectById(5L)).thenReturn(user(5L, "admin", 1));
        loginAs(5L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.disable(5L));

        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("请勿自操作"));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("禁用：正常用户 → 状态置 2")
    void disable_success() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "teacher2", 1));
        loginAs(1L); // 管理员 id=1，禁用的是 id=2

        service.disable(2L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("禁用：同一个用户被查两次也只更新一次（无重复写）")
    void disable_updatesExactlyOnce() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "teacher2", 1));
        loginAs(1L);

        service.disable(2L);

        verify(userMapper, times(1)).updateById(any(User.class));
        verify(userMapper, times(1)).selectById(2L);
    }
}
