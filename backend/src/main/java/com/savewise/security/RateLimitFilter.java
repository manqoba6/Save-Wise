package com.savewise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savewise.exception.ErrorResponse;
import com.savewise.util.SlidingWindowLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Slows down password guessing and sign-up / reset-email abuse: a limited number of attempts per IP per window. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password");

    private final SlidingWindowLimiter limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(ObjectMapper mapper,
                           @Value("${app.rate-limit.max-requests}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds}") long windowSeconds) {
        this.mapper = mapper;
        this.limiter = new SlidingWindowLimiter(maxRequests, Duration.ofSeconds(windowSeconds).toMillis());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        long retryAfter = limiter.tryAcquire(key, System.currentTimeMillis());
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), new ErrorResponse(Instant.now(), 429, "Too Many Requests",
                "Too many attempts. Please wait " + retryAfter + " seconds and try again.", request.getRequestURI()));
    }
}
