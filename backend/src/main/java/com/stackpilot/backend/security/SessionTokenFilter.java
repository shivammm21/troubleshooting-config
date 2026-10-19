package com.stackpilot.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires {@code X-StackPilot-Token} on all local API routes except health.
 */
@Component
public class SessionTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-StackPilot-Token";

    private final SessionTokenService tokens;

    public SessionTokenFilter(SessionTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/health".equals(path) || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!tokens.matches(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorised\",\"code\":\"UNAUTHORIZED\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
