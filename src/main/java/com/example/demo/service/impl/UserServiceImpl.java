package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.AdminUserResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void register(UserRegisterRequestDTO userRegisterRequestDTO) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, userRegisterRequestDTO.getUsername()));

        if (count !=null && count >  0){
            throw new BusinessException(409,"用户名已被占用");
        }
        String password = passwordEncoder.encode(userRegisterRequestDTO.getPassword());

        // 显式写死 role/status，不依赖数据库列默认值：
        // 默认值只存在于某台机器的 DDL 里，一旦换库/改表就悄悄变成 null，
        // 而"教师注册后必须待审核"是业务规则，应该由代码保证。
        User user = User.builder().username(userRegisterRequestDTO.getUsername())
                .password(password).
                realName(userRegisterRequestDTO.getRealName())
                .role("teacher")
                .status(0)
                .build();
        userMapper.insert(user);

    }

    @Override
    public UserLoginResponseDTO login(UserLoginRequestDTO userLoginRequestDTO) {
        User user = userMapper.selectByName(userLoginRequestDTO.getUsername());

        if (user == null || !passwordEncoder.matches(userLoginRequestDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(401,"用户名或密码错误");
        }
        if(user.getStatus() == 0){
            throw new BusinessException(403 , "待审核");
        }

        if(user.getStatus() ==2){
            throw new BusinessException(403,"用户已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(),user.getUsername(),user.getRole());
        return UserLoginResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .token(token)
                .build();

    }

    @Override
    @Transactional
    public UserLoginResponseDTO changePassword(Long currentUserId, ChangePasswordRequestDTO dto) {
        // 1. 取当前用户（按 id，不可能被别人冒充——id 来自过滤器验证过的 token）
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(401, "登录状态异常，请重新登录");
        }

        // 2. 旧密码必须对得上：防止 token 被盗后，盗号者直接改密霸占账号
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "当前密码不正确");
        }

        // 3. 新旧密码不能相同（否则"改密下线"失去意义）
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与当前密码相同");
        }

        // 4. 更新密码 + 划线（同一事务：要么都成功，要么都不改）
        //    pwdChangeTime = now 之后，所有 iat < now 的旧 token 全部失效
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setPwdChangeTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 5. 失效划线缓存：改密请求自身过过滤器时会把旧划线时间写进缓存（TTL 60s），
        //    不删的话改密后 60 秒内旧 token 会被旧划线误放行，"立即下线"被缓存打穿
        tokenBlacklistService.invalidatePwdTimeCache(user.getId());

        // 6. 给当前用户重新签发 token（iat 晚于划线时间），当前会话不掉线
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return UserLoginResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .token(token)
                .build();
    }

    @Override
    public PageResult<AdminUserResponse> page(UserReviewQueryDTO userReviewQueryDTO) {
        String keyword = userReviewQueryDTO.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String likeKeyword = hasKeyword ? "%" + keyword.trim() + "%" : null;

        Page<User> page= userMapper.selectPage(new Page<User>(userReviewQueryDTO.getPage(), userReviewQueryDTO.getPageSize())
                , new LambdaQueryWrapper<User>().eq( userReviewQueryDTO.getStatus() != null, User::getStatus, userReviewQueryDTO.getStatus())
                        .and(hasKeyword,
                        w -> w.like(User::getUsername, likeKeyword)).orderByDesc(User::getId));
        List<AdminUserResponse> records = page.getRecords().stream()
                .map(AdminUserResponse::from)
                .toList();
        return new PageResult<>(page.getTotal(), records, page.getCurrent(), page.getSize());
    }

    @Override
    public void approve(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) { throw new BusinessException(404, "用户不存在"); }

        if (user.getStatus() != 0) {          // 不是待审核？一律不见
            throw new BusinessException(409, "仅待审核用户需要审核");
        }
        //线程获取当前用户id,防止自操作
        user.setStatus(1);
        userMapper.updateById(user);
    }

    @Override
    public void disable(Long id) {
        User user = userMapper.selectById(id);
        if (user == null){
            throw new BusinessException(404,"用户不存在");
        }
        if(user.getStatus() == 0){
            throw new BusinessException(409,"请求失败，当前用户审核中..");
        }
        if(user.getStatus() == 2){
            throw new BusinessException(409,"用户已禁用");
        }
        //线程获取当前用户id,防止自删
        Long currentId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(currentId.equals(user.getId())){
            throw new BusinessException(409,"请勿自操作");
        }
        user.setStatus(2);
        userMapper.updateById(user);

    }
}
