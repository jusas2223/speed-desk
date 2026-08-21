package com.speeddesk.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("speeddesk.account")
public record AccountProperties(
        @Min(value = 5, message = "A recuperação deve durar ao menos 5 minutos")
        @Max(value = 1440, message = "A recuperação deve durar no máximo 1440 minutos")
        long passwordResetExpirationMinutes
) {
}
