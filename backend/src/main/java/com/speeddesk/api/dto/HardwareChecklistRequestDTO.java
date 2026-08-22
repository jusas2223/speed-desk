package com.speeddesk.api.dto;

import jakarta.validation.constraints.Size;

public record HardwareChecklistRequestDTO(
        boolean equipmentTurnsOn,
        boolean functionalityValidated,
        boolean connectivityValidated,
        boolean cleaningCompleted,
        boolean clientDataPreserved,

        @Size(max = 2000, message = "As observações devem possuir no máximo 2000 caracteres")
        String notes
) {
}
