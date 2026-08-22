package com.speeddesk.api.security;

import com.speeddesk.api.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ProblemDetailWriter problemDetailWriter;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestsSinceCleanup = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        int limit = authenticated
                ? properties.authenticatedRequestsPerMinute()
                : properties.publicRequestsPerMinute();
        String identity = authenticated
                ? "user:" + authentication.getName()
                : "ip:" + request.getRemoteAddr();
        Instant windowStart = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        String key = identity + ':' + windowStart.toEpochMilli();

        WindowCounter counter = counters.computeIfAbsent(
                key,
                ignored -> new WindowCounter(windowStart, new AtomicLong())
        );
        long used = counter.count().incrementAndGet();
        long remaining = Math.max(0, limit - used);
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(remaining));

        cleanupExpiredWindows(windowStart);

        if (used > limit) {
            long retryAfter = Math.max(
                    1,
                    60 - Math.floorMod(clock.instant().getEpochSecond(), 60)
            );
            response.setHeader("Retry-After", Long.toString(retryAfter));
            problemDetailWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de requisições excedido",
                    "Aguarde antes de realizar novas requisições."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void cleanupExpiredWindows(Instant currentWindow) {
        if (requestsSinceCleanup.incrementAndGet() % 256 != 0) return;
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart().isBefore(currentWindow));
    }

    private record WindowCounter(Instant windowStart, AtomicLong count) {
    }
}
