package com.example.springb.security;

import com.example.springb.mapper.SysPermissionMapper;
import com.example.springb.mapper.TokenBlacklistMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SecurityUtils {

    private static final Set<String> ADMIN_PERMISSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "admin:read", "admin:write", "admin:delete",
            "detect:read", "detect:write", "detect:delete",
            "video:read", "video:upload", "video:delete",
            "data:read", "data:export",
            "file:read", "file:upload", "file:delete",
            "system:config"
    )));

    private static final Set<String> OPERATOR_PERMISSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "detect:read", "detect:write",
            "video:read", "video:upload",
            "data:read", "data:export",
            "file:read", "file:upload"
    )));

    private static final Set<String> VIEWER_PERMISSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "detect:read", "video:read", "data:read", "file:read"
    )));

    @Resource
    private SysPermissionMapper permissionMapper;

    @Resource
    private TokenBlacklistMapper tokenBlacklistMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    private final Map<String, Long> blacklistedTokenCache = new ConcurrentHashMap<>();
    private final Map<Integer, CachedPermissions> permissionCache = new ConcurrentHashMap<>();

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (!isBcrypt(storedPassword)) {
            return rawPassword.equals(storedPassword);
        }
        try {
            return passwordEncoder.matches(rawPassword, storedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    public boolean isTokenBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }

        Long cachedExpireAt = blacklistedTokenCache.get(jti);
        long now = System.currentTimeMillis();
        if (cachedExpireAt != null) {
            if (cachedExpireAt > now) {
                return true;
            }
            blacklistedTokenCache.remove(jti);
        }

        try {
            boolean exists = tokenBlacklistMapper.countValidByJti(jti) > 0;
            if (exists) {
                blacklistedTokenCache.put(jti, now + 7L * 24 * 60 * 60 * 1000);
            }
            return exists;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void addTokenToBlacklist(String jti, Date expireTime) {
        if (jti == null || jti.isEmpty() || expireTime == null) {
            return;
        }
        blacklistedTokenCache.put(jti, expireTime.getTime());
        try {
            tokenBlacklistMapper.insert(jti, expireTime);
        } catch (RuntimeException ignored) {
        }
    }

    public Set<String> getUserPermissions(Integer userId) {
        return getUserPermissions(userId, Collections.emptyList());
    }

    public Set<String> getUserPermissions(Integer userId, Collection<String> roles) {
        if (userId == null) {
            return permissionsForRoles(roles);
        }

        CachedPermissions cached = permissionCache.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt > now) {
            return cached.permissions;
        }

        try {
            List<String> permissions = permissionMapper.selectPermissionsByUserId(userId);
            if (permissions != null && !permissions.isEmpty()) {
                Set<String> permissionSet = new HashSet<>(permissions);
                permissionCache.put(userId, new CachedPermissions(permissionSet, now + 30L * 60 * 1000));
                return permissionSet;
            }
        } catch (RuntimeException ignored) {
        }

        Set<String> fallback = permissionsForRoles(roles);
        permissionCache.put(userId, new CachedPermissions(fallback, now + 5L * 60 * 1000));
        return fallback;
    }

    public Set<String> permissionsForRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            String normalizedRole = normalizeRoleCode(role);
            if ("ADMIN".equals(normalizedRole)) {
                permissions.addAll(ADMIN_PERMISSIONS);
            } else if ("OPERATOR".equals(normalizedRole)) {
                permissions.addAll(OPERATOR_PERMISSIONS);
            } else if ("VIEWER".equals(normalizedRole)) {
                permissions.addAll(VIEWER_PERMISSIONS);
            }
        }
        return permissions;
    }

    public List<String> normalizeRoleCodes(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String role : roles) {
            String normalized = normalizeRoleCode(role);
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public String normalizeRoleCode(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toUpperCase();
        if ("ADMIN".equals(normalized) || "管理员".equals(role)) {
            return "ADMIN";
        }
        if ("VIEWER".equals(normalized) || "查看者".equals(role)) {
            return "VIEWER";
        }
        if ("OPERATOR".equals(normalized) || "USER".equals(normalized) || "普通用户".equals(role) || "操作员".equals(role)) {
            return "OPERATOR";
        }
        return normalized;
    }

    public String toLegacyRole(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return "user";
        }
        if (roleCodes.stream().anyMatch(role -> "ADMIN".equals(normalizeRoleCode(role)))) {
            return "admin";
        }
        if (roleCodes.stream().anyMatch(role -> "VIEWER".equals(normalizeRoleCode(role)))) {
            return "viewer";
        }
        return "user";
    }

    public void clearUserPermissionCache(Integer userId) {
        if (userId != null) {
            permissionCache.remove(userId);
        }
    }

    private static class CachedPermissions {
        private final Set<String> permissions;
        private final long expireAt;

        private CachedPermissions(Set<String> permissions, long expireAt) {
            this.permissions = permissions;
            this.expireAt = expireAt;
        }
    }
}
