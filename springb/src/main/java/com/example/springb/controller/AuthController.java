package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.service.AuthService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Map<String, Object> result = authService.login(
                params.get("username"),
                params.get("password"),
                getClientIp(request)
        );
        return Result.success(result);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = resolveBearerToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result refresh(@RequestBody Map<String, String> params) {
        return Result.success(authService.refreshToken(params.get("refreshToken")));
    }

    @GetMapping("/info")
    public Result getUserInfo(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("currentUserId");
        String username = (String) request.getAttribute("currentUsername");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        return Result.success(authService.getCurrentUserInfo(userId, username, roles == null ? Collections.emptyList() : roles));
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
