package com.speeddesk.api.repository;

import com.speeddesk.api.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByKeyHashAndActor(String keyHash, String actor);

    long deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
