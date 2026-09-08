package com.example.springb.security;

import com.example.springb.annotation.RequirePermission;
import com.example.springb.exception.CustomerException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Aspect
@Component
public class PermissionAspect {

    @Resource
    private SecurityUtils securityUtils;

    @Around("@within(com.example.springb.annotation.RequirePermission) || @annotation(com.example.springb.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission requirePermission = findRequirePermission(joinPoint);
        if (requirePermission == null || requirePermission.value().length == 0) {
            return joinPoint.proceed();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new CustomerException("401", "无法获取请求信息");
        }

        HttpServletRequest request = attributes.getRequest();
        Integer userId = (Integer) request.getAttribute("currentUserId");
        if (userId == null) {
            throw new CustomerException("401", "用户未认证");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        if (roles == null) {
            roles = Collections.emptyList();
        }

        Set<String> userPermissions = securityUtils.getUserPermissions(userId, roles);
        List<String> requiredPermissions = Arrays.asList(requirePermission.value());
        boolean hasPermission;
        if (requirePermission.logical() == RequirePermission.Logical.OR) {
            hasPermission = requiredPermissions.stream().anyMatch(userPermissions::contains);
        } else {
            hasPermission = userPermissions.containsAll(requiredPermissions);
        }

        if (!hasPermission) {
            throw new CustomerException("4030", "权限不足");
        }
        return joinPoint.proceed();
    }

    private RequirePermission findRequirePermission(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        if (annotation != null) {
            return annotation;
        }
        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
