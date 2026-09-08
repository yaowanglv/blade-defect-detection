package com.example.springb.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.example.springb.entity.Admin;
import com.example.springb.exception.CustomerException;
import com.example.springb.mapper.AdminMapper;
import com.example.springb.mapper.SysRoleMapper;
import com.example.springb.security.JwtUtils;
import com.example.springb.security.SecurityUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    @Resource
    private AdminMapper adminMapper;

    @Resource
    private SysRoleMapper roleMapper;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private SecurityUtils securityUtils;

    public Map<String, Object> login(String username, String password, String loginIp) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new CustomerException("400", "用户名和密码不能为空");
        }

        Admin dbAdmin = adminMapper.selectByUsername(username);
        if (dbAdmin == null || !securityUtils.matchesPassword(password, dbAdmin.getPassword())) {
            throw new CustomerException("401", "用户名或密码错误");
        }
        if (dbAdmin.getStatus() != null && dbAdmin.getStatus() == 0) {
            throw new CustomerException("4031", "账号已被禁用");
        }

        migratePasswordIfNeeded(dbAdmin, password);

        List<String> roles = loadRoleCodes(dbAdmin);
        Set<String> permissions = securityUtils.getUserPermissions(dbAdmin.getId(), roles);

        String accessToken = jwtUtils.generateAccessToken(dbAdmin.getId(), dbAdmin.getUsername(), roles);
        String refreshToken = jwtUtils.generateRefreshToken(dbAdmin.getId());

        try {
            adminMapper.updateLastLoginInfo(dbAdmin.getId(), DateUtil.now(), loginIp);
        } catch (RuntimeException ignored) {
        }

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("tokenType", "Bearer");
        result.put("expiresIn", jwtUtils.getAccessTokenExpire());
        result.put("userInfo", buildUserInfo(dbAdmin, roles, permissions));
        return result;
    }

    public void logout(String token) {
        try {
            String jti = jwtUtils.getJti(token);
            Date expireTime = jwtUtils.getExpirationDate(token);
            securityUtils.addTokenToBlacklist(jti, expireTime);
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken) || !jwtUtils.validateToken(refreshToken)) {
            throw new CustomerException("4012", "RefreshToken无效或已过期");
        }
        if (!"refresh".equals(jwtUtils.getTokenType(refreshToken))) {
            throw new CustomerException("4012", "Token类型无效");
        }

        Integer userId = jwtUtils.getUserId(refreshToken);
        Admin admin = adminMapper.selectById(userId);
        if (admin == null) {
            throw new CustomerException("401", "用户不存在");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new CustomerException("4031", "账号已被禁用");
        }

        List<String> roles = loadRoleCodes(admin);
        String newAccessToken = jwtUtils.generateAccessToken(userId, admin.getUsername(), roles);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("expiresIn", jwtUtils.getAccessTokenExpire());
        return result;
    }

    public Map<String, Object> getCurrentUserInfo(Integer userId, String username, List<String> roles) {
        Admin admin = userId == null ? null : adminMapper.selectById(userId);
        List<String> roleCodes = roles == null || roles.isEmpty()
                ? (admin == null ? Collections.emptyList() : loadRoleCodes(admin))
                : securityUtils.normalizeRoleCodes(roles);
        Set<String> permissions = securityUtils.getUserPermissions(userId, roleCodes);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", userId);
        userInfo.put("username", username);
        if (admin != null) {
            userInfo.put("name", admin.getName());
            userInfo.put("role", admin.getRole());
        }
        userInfo.put("roles", roleCodes);
        userInfo.put("permissions", permissions);
        return userInfo;
    }

    private void migratePasswordIfNeeded(Admin admin, String rawPassword) {
        if (securityUtils.isBcrypt(admin.getPassword())) {
            return;
        }
        Admin updateAdmin = new Admin();
        updateAdmin.setId(admin.getId());
        updateAdmin.setPassword(securityUtils.encodePassword(rawPassword));
        adminMapper.updatePasswordById(updateAdmin);
    }

    private List<String> loadRoleCodes(Admin admin) {
        try {
            List<String> roles = roleMapper.selectRoleCodesByUserId(admin.getId());
            if (roles != null && !roles.isEmpty()) {
                return securityUtils.normalizeRoleCodes(roles);
            }
        } catch (RuntimeException ignored) {
        }
        return securityUtils.normalizeRoleCodes(Collections.singletonList(admin.getRole()));
    }

    private Map<String, Object> buildUserInfo(Admin admin, List<String> roles, Set<String> permissions) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", admin.getId());
        userInfo.put("username", admin.getUsername());
        userInfo.put("name", admin.getName());
        userInfo.put("role", securityUtils.toLegacyRole(roles));
        userInfo.put("roles", roles);
        userInfo.put("permissions", new HashSet<>(permissions));
        return userInfo;
    }
}
