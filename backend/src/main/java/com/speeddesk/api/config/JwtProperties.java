package com.speeddesk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("speeddesk.jwt")
public record JwtProperties(
        String secret,
        long expirationSeconds,
        String issuer
) {
}
