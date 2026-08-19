package com.speeddesk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("speeddesk.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
