package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.common.BizException;
import com.smartmaterials.dto.LoginRequest;
import com.smartmaterials.dto.LoginResponse;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.SysUserMapper;
import com.smartmaterials.security.JwtService;
import com.smartmaterials.support.AppTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 登录鉴权：校验账号密码 → 签发 JWT。 */
@Service
public class AuthService {

    private static final List<String> WRITE_ROLES = List.of("lab", "admin", "buyer");
    private static final List<String> APPROVE_ROLES = List.of("teacher", "admin");
    private static final List<String> EXPORT_ROLES = List.of("student", "lab", "teacher", "buyer", "admin");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getLdapUid, request.getUsername().trim())
                .last("LIMIT 1"));

        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BizException.forbidden("账号已停用，请联系管理员");
        }

        user.setLastLoginTime(LocalDateTime.now(AppTime.ZONE));
        user.setUpdateTime(LocalDateTime.now(AppTime.ZONE));
        userMapper.updateById(user);

        String token = jwtService.createToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpireMinutes(), user, permissions(user));
    }

    public LoginResponse.Permissions permissions(SysUser user) {
        String role = user.getRole() == null ? "guest" : user.getRole();
        return new LoginResponse.Permissions(
                WRITE_ROLES.contains(role),
                "admin".equals(role),
                APPROVE_ROLES.contains(role),
                EXPORT_ROLES.contains(role));
    }
}
