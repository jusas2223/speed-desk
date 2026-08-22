package com.speeddesk.api.service;

import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class RealtimeService {

    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final UserRepository userRepository;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        send(userId, emitter, "connected", Map.of(
                "connectedAt", OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
        return emitter;
    }

    public void publishAfterCommit(UUID userId, String eventName, Object payload) {
        afterCommit(() -> publish(userId, eventName, payload));
    }

    public void publishToRoleAfterCommit(
            UserRole role,
            UUID excludedUserId,
            String eventName,
            Object payload
    ) {
        afterCommit(() -> userRepository.findAllByRoleAndActiveTrue(role).stream()
                .map(user -> user.getId())
                .filter(id -> excludedUserId == null || !excludedUserId.equals(id))
                .forEach(id -> publish(id, eventName, payload)));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }

    private void publish(UUID userId, String eventName, Object payload) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.forEach(emitter -> send(userId, emitter, eventName, payload));
    }

    private void send(
            UUID userId,
            SseEmitter emitter,
            String eventName,
            Object payload
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(UUID.randomUUID().toString())
                    .data(payload));
        } catch (IOException | IllegalStateException exception) {
            remove(userId, emitter);
            emitter.complete();
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
    }
}
