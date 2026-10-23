package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.AdminUserResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public void register(UserRegisterRequestDTO userRegisterRequestDTO) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, userRegisterRequestDTO.getUsername()));

        if (count !=null && count >  0){
            throw new BusinessException(409,"用户名已被占用");
        }
        String password = passwordEncoder.encode(userRegisterRequestDTO.getPassword());

        User user = User.builder().username(userRegisterRequestDTO.getUsername())
                .password(password).
                realName(userRegisterRequestDTO.getRealName())
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
