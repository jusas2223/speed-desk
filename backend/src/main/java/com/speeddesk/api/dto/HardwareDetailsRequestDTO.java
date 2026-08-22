package com.speeddesk.api.dto;

import com.speeddesk.api.entity.HardwareEligibilityStatus;
import com.speeddesk.api.entity.HardwareMaintenanceStage;
import com.speeddesk.api.entity.HardwareWarrantyCoverage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HardwareDetailsRequestDTO(
        @NotNull(message = "A elegibilidade do atendimento é obrigatória")
        HardwareEligibilityStatus eligibilityStatus,

        @NotNull(message = "A cobertura de garantia é obrigatória")
        HardwareWarrantyCoverage warrantyCoverage,

        @Size(max = 2000, message = "As observações de elegibilidade devem possuir no máximo 2000 caracteres")
        String eligibilityNotes,

        @NotNull(message = "A etapa de manutenção é obrigatória")
        HardwareMaintenanceStage maintenanceStage
) {
}
