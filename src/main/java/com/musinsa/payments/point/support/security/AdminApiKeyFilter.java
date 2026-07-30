package com.musinsa.payments.point.support.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musinsa.payments.point.config.AdminSecurityProperties;
import com.musinsa.payments.point.support.error.ErrorResponse;
import com.musinsa.payments.point.support.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Api-Key";
    private static final String ADMIN_PATH = "/api/v1/admin/";

    private final AdminSecurityProperties adminProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!hasValidApiKey(request)) {
            log.warn("rejected admin request {} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasValidApiKey(HttpServletRequest request) {
        String presented = request.getHeader(HEADER);
        if (presented == null || adminProperties.getApiKey() == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                adminProperties.getApiKey().getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                "UNAUTHORIZED", "관리자 API 인증에 실패했습니다.", MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
