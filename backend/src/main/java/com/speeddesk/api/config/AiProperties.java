package com.speeddesk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("speeddesk.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        int timeoutSeconds
) {
    public AiProperties {
        if (model == null || model.isBlank()) model = "gemini-2.5-flash-lite";
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        }
        if (timeoutSeconds <= 0) timeoutSeconds = 20;
    }

    public boolean remoteAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
