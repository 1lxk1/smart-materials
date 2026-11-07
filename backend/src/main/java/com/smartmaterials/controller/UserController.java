package com.smartmaterials.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户与权限。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserMapper userMapper;

    public UserController(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public List<SysUser> list() {
        return userMapper.selectList(Wrappers.lambdaQuery(SysUser.class)
                .orderByAsc(SysUser::getId));
    }
}
