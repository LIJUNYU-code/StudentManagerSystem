package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

        String token = jwtUtil.generateToken(user.getId(),user.getUsername());
        return UserLoginResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .token(token)
                .build();

    }
}
