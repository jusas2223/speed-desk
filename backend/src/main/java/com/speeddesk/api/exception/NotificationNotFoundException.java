package com.speeddesk.api.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notificação não encontrada: " + notificationId);
    }
}
