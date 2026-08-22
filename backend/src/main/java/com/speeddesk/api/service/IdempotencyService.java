package com.speeddesk.api.service;

import com.speeddesk.api.entity.IdempotencyRecord;
import com.speeddesk.api.entity.IdempotencyState;
import com.speeddesk.api.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final long RETENTION_HOURS = 24;

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    @Transactional
    public BeginResult begin(
            String keyHash,
            String actor,
            String method,
            String path,
            String fingerprint
    ) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        repository.deleteByExpiresAtBefore(now);
        IdempotencyRecord existing = repository.findByKeyHashAndActor(keyHash, actor)
                .orElse(null);

        if (existing != null && !existing.getExpiresAt().isAfter(now)) {
            repository.delete(existing);
            repository.flush();
            existing = null;
        }

        if (existing != null) {
            boolean sameRequest = existing.getMethod().equals(method)
                    && existing.getRequestPath().equals(path)
                    && existing.getRequestFingerprint().equals(fingerprint);
            if (!sameRequest) return BeginResult.conflict();
            if (existing.getState() == IdempotencyState.PROCESSING) {
                return BeginResult.processing();
            }
            return BeginResult.replay(
                    existing.getResponseStatus(),
                    existing.getResponseContentType(),
                    existing.getResponseBody()
            );
        }

        IdempotencyRecord created = repository.saveAndFlush(IdempotencyRecord.builder()
                .keyHash(keyHash)
                .actor(actor)
                .method(method)
                .requestPath(path)
                .requestFingerprint(fingerprint)
                .state(IdempotencyState.PROCESSING)
                .createdAt(now)
                .expiresAt(now.plusHours(RETENTION_HOURS))
                .build());
        return BeginResult.created(created.getId());
    }

    @Transactional
    public void complete(UUID id, int status, String contentType, String body) {
        repository.findById(id).ifPresent(record -> {
            record.setState(IdempotencyState.COMPLETED);
            record.setResponseStatus(status);
            record.setResponseContentType(contentType);
            record.setResponseBody(body);
        });
    }

    @Transactional
    public void discard(UUID id) {
        repository.deleteById(id);
    }

    public enum BeginStatus {
        CREATED,
        REPLAY,
        PROCESSING,
        CONFLICT
    }

    public record BeginResult(
            BeginStatus status,
            UUID recordId,
            Integer responseStatus,
            String responseContentType,
            String responseBody
    ) {
        static BeginResult created(UUID id) {
            return new BeginResult(BeginStatus.CREATED, id, null, null, null);
        }

        static BeginResult replay(Integer status, String contentType, String body) {
            return new BeginResult(BeginStatus.REPLAY, null, status, contentType, body);
        }

        static BeginResult processing() {
            return new BeginResult(BeginStatus.PROCESSING, null, null, null, null);
        }

        static BeginResult conflict() {
            return new BeginResult(BeginStatus.CONFLICT, null, null, null, null);
        }
    }
}
