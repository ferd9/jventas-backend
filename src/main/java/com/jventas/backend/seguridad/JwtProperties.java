package com.jventas.backend.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jventas.security.jwt")
public record JwtProperties(String secret, long expirationMinutes, long refreshExpirationDays) {

    public JwtProperties {
        if (expirationMinutes <= 0) {
            expirationMinutes = 30;
        }
        if (refreshExpirationDays <= 0) {
            refreshExpirationDays = 7;
        }
    }
}
