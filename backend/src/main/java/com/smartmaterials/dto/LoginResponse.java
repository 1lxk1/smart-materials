package com.smartmaterials.dto;

import com.smartmaterials.entity.SysUser;

/** 登录响应：JWT + 用户信息 + 前端需要的权限位。 */
public record LoginResponse(String token, String tokenType, long expiresInMinutes, SysUser user,
                            Permissions permissions) {

    public record Permissions(boolean canWriteStock, boolean canManageDict,
                              boolean canApprove, boolean canExport) {
    }
}
