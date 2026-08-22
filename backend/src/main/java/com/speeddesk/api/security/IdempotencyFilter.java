package com.speeddesk.api.security;

import com.speeddesk.api.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    private final IdempotencyService service;
    private final ProblemDetailWriter problemDetailWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean modifying = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        if (!modifying) return true;

        return !(path.equals("/api/tickets")
                || path.startsWith("/api/tickets/")
                || path.equals("/api/users")
                || path.startsWith("/api/users/")
                || path.equals("/api/assets")
                || path.startsWith("/api/assets/")
                || path.equals("/api/incidents")
                || path.startsWith("/api/incidents/")
                || path.equals("/api/organizations")
                || path.equals("/api/ticket-categories")
                || path.startsWith("/api/sla-policies/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        key = key.trim();
        if (key.length() < 8 || key.length() > 128) {
            problemDetailWriter.write(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    "Chave de idempotência inválida",
                    "Idempotency-Key deve possuir entre 8 e 128 caracteres."
            );
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String path = request.getRequestURI();
        if (request.getQueryString() != null) path += '?' + request.getQueryString();
        String fingerprint = sha256(
                request.getMethod() + "\n" + path + "\n"
                        + new String(cachedRequest.body(), StandardCharsets.UTF_8)
        );

        IdempotencyService.BeginResult begin;
        try {
            begin = service.begin(
                    sha256(key),
                    authentication.getName(),
                    request.getMethod(),
                    path,
                    fingerprint
            );
        } catch (DataIntegrityViolationException exception) {
            writeProcessingConflict(request, response);
            return;
        }

        switch (begin.status()) {
            case REPLAY -> replay(response, begin);
            case PROCESSING -> writeProcessingConflict(request, response);
            case CONFLICT -> problemDetailWriter.write(
                    request,
                    response,
                    HttpStatus.CONFLICT,
                    "Chave de idempotência reutilizada",
                    "A mesma chave já foi usada com outra operação ou conteúdo."
            );
            case CREATED -> executeAndCache(cachedRequest, response, filterChain, begin);
        }
    }

    private void executeAndCache(
            CachedBodyHttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            IdempotencyService.BeginResult begin
    ) throws IOException, ServletException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
            if (wrapper.getStatus() < 500) {
                service.complete(
                        begin.recordId(),
                        wrapper.getStatus(),
                        wrapper.getContentType(),
                        new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)
                );
            } else {
                service.discard(begin.recordId());
            }
        } catch (IOException | ServletException | RuntimeException exception) {
            service.discard(begin.recordId());
            throw exception;
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    private void replay(HttpServletResponse response, IdempotencyService.BeginResult begin)
            throws IOException {
        response.setStatus(begin.responseStatus());
        if (begin.responseContentType() != null) {
            response.setContentType(begin.responseContentType());
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Idempotency-Replayed", "true");
        if (begin.responseBody() != null) {
            response.getOutputStream().write(begin.responseBody().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeProcessingConflict(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        problemDetailWriter.write(
                request,
                response,
                HttpStatus.CONFLICT,
                "Operação idempotente em andamento",
                "Uma operação com esta chave já está sendo processada."
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
