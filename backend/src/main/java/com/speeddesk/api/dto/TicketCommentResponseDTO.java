package com.speeddesk.api.dto;

import com.speeddesk.api.entity.TicketComment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketCommentResponseDTO(
        UUID id,
        String content,
        boolean internal,
        UserResponseDTO author,
        OffsetDateTime createdAt
) {
    public static TicketCommentResponseDTO from(TicketComment comment) {
        return new TicketCommentResponseDTO(
                comment.getId(),
                comment.getContent(),
                comment.isInternal(),
                UserResponseDTO.from(comment.getAuthor()),
                comment.getCreatedAt()
        );
    }
}
