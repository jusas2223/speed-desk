package com.speeddesk.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TicketFinalizeRequestDTO(
        @NotNull(message = "O valor final é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor final deve ser maior que zero")
        @Digits(
                integer = 10,
                fraction = 2,
                message = "O valor final deve possuir no máximo duas casas decimais"
        )
        BigDecimal valorFinal
) {
}
