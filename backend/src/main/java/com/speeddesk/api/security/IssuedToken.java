package com.speeddesk.api.security;

public record IssuedToken(String value, long expiresIn) {
}
