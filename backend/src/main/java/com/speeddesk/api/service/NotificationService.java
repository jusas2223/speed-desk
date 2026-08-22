package com.speeddesk.api.service;

import com.speeddesk.api.dto.NotificationResponseDTO;
import com.speeddesk.api.dto.NotificationSummaryDTO;
import com.speeddesk.api.entity.Notification;
import com.speeddesk.api.entity.NotificationType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.NotificationNotFoundException;
import com.speeddesk.api.repository.NotificationRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final RealtimeService realtimeService;
    private final Clock clock;

    public List<NotificationResponseDTO> list() {
        UUID userId = authorizationService.currentUser().id();
        return notificationRepository.findAllByRecipient_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponseDTO::from)
                .toList();
    }

    public NotificationSummaryDTO summary() {
        UUID userId = authorizationService.currentUser().id();
        return new NotificationSummaryDTO(
                notificationRepository.countByRecipient_IdAndReadAtIsNull(userId)
        );
    }

    @Transactional
    public NotificationResponseDTO markRead(UUID notificationId) {
        UUID userId = authorizationService.currentUser().id();
        Notification notification = notificationRepository
                .findByIdAndRecipient_Id(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now(clock));
            notificationRepository.saveAndFlush(notification);
        }
        return NotificationResponseDTO.from(notification);
    }

    @Transactional
    public NotificationSummaryDTO markAllRead() {
        UUID userId = authorizationService.currentUser().id();
        notificationRepository.markAllRead(userId, OffsetDateTime.now(clock));
        realtimeService.publishAfterCommit(
                userId,
                "notifications-read",
                new NotificationSummaryDTO(0)
        );
        return new NotificationSummaryDTO(0);
    }

    @Transactional
    public void notifyUsers(
            Set<UUID> recipientIds,
            UUID excludedUserId,
            NotificationType type,
            String title,
            String message,
            String resourceType,
            UUID resourceId
    ) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(recipientIds);
        if (excludedUserId != null) uniqueIds.remove(excludedUserId);
        userRepository.findAllById(uniqueIds).stream()
                .filter(User::isActive)
                .forEach(user -> create(
                        user,
                        type,
                        title,
                        message,
                        resourceType,
                        resourceId
                ));
    }

    @Transactional
    public void notifyRole(
            UserRole role,
            UUID excludedUserId,
            NotificationType type,
            String title,
            String message,
            String resourceType,
            UUID resourceId
    ) {
        userRepository.findAllByRoleAndActiveTrue(role).stream()
                .filter(user -> excludedUserId == null
                        || !excludedUserId.equals(user.getId()))
                .forEach(user -> create(
                        user,
                        type,
                        title,
                        message,
                        resourceType,
                        resourceId
                ));
    }

    private void create(
            User recipient,
            NotificationType type,
            String title,
            String message,
            String resourceType,
            UUID resourceId
    ) {
        Notification saved = notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .createdAt(OffsetDateTime.now(clock))
                .build());
        NotificationResponseDTO response = NotificationResponseDTO.from(saved);
        realtimeService.publishAfterCommit(recipient.getId(), "notification", response);
    }
}
