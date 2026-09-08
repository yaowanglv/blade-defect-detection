package com.example.springb.security;

import com.example.springb.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Order(1)
public class JwtAuthFilter implements Filter {

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/login",
            "/public/",
            "/files/",
            "/favicon.ico",
            "/error",
            "/"
    );

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private SecurityUtils securityUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = getRequestPath(httpRequest);
        if (isWhiteListed(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(httpRequest, path);
        if (token == null || token.isBlank()) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "401", "未提供认证Token");
            return;
        }

        Claims claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (ExpiredJwtException e) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "4010", "Token已过期");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "401", "Token无效");
            return;
        }

        if (!"access".equals(claims.get("type", String.class))) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "401", "Token类型无效");
            return;
        }

        String jti = claims.getId();
        if (jti != null && securityUtils.isTokenBlacklisted(jti)) {
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "4011", "Token已被注销");
            return;
        }

        Integer userId = Integer.parseInt(claims.getSubject());
        String username = claims.get("username", String.class);
        List<String> roles = extractRoles(claims);

        httpRequest.setAttribute("currentUserId", userId);
        httpRequest.setAttribute("currentUsername", username);
        httpRequest.setAttribute("currentRoles", roles);
        httpRequest.setAttribute("currentTokenJti", jti);

        chain.doFilter(request, response);
    }

    private String getRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(item -> {
            if ("/".equals(item)) {
                return "/".equals(path);
            }
            return path.equals(item) || (item.endsWith("/") && path.startsWith(item));
        });
    }

    private String resolveToken(HttpServletRequest request, String path) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (path != null && path.startsWith("/video/download/")) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
