package com.speeddesk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "speeddesk.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int authenticatedRequestsPerMinute,
        int publicRequestsPerMinute
) {
    public RateLimitProperties {
        if (authenticatedRequestsPerMinute <= 0) {
            authenticatedRequestsPerMinute = 180;
        }
        if (publicRequestsPerMinute <= 0) {
            publicRequestsPerMinute = 20;
        }
    }
}
