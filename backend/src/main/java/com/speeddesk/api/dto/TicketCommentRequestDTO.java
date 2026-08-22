package com.speeddesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketCommentRequestDTO(
        @NotBlank(message = "O conteúdo do comentário é obrigatório.")
        @Size(max = 4000, message = "O comentário deve possuir no máximo 4000 caracteres.")
        String content,

        boolean internal
) {
    public TicketCommentRequestDTO {
        content = content == null ? null : content.trim();
    }
}
