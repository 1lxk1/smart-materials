package com.smartmaterials.controller;

import com.smartmaterials.dto.LoginRequest;
import com.smartmaterials.dto.LoginResponse;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.service.AuthService;
import com.smartmaterials.service.StockService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录与当前用户信息。 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final StockService stockService;

    public AuthController(AuthService authService, StockService stockService) {
        this.authService = authService;
        this.stockService = stockService;
    }

    /** 健康检查（Render 探针 / 前端连通性自检）。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "smart-materials-server");
        body.put("time", java.time.LocalDateTime.now().toString());
        return body;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal SysUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", user);
        body.put("permissions", authService.permissions(user));
        body.put("quota", stockService.quota(user));
        return body;
    }
}
