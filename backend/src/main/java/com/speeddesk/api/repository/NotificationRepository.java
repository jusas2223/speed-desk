package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByRecipient_IdOrderByCreatedAtDesc(UUID recipientId);

    Optional<Notification> findByIdAndRecipient_Id(UUID id, UUID recipientId);

    long countByRecipient_IdAndReadAtIsNull(UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.readAt = :readAt
             where notification.recipient.id = :recipientId
               and notification.readAt is null
            """)
    int markAllRead(
            @Param("recipientId") UUID recipientId,
            @Param("readAt") OffsetDateTime readAt
    );
}
