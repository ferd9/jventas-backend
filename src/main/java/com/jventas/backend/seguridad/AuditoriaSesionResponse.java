package com.jventas.backend.seguridad;

import java.time.Instant;

public record AuditoriaSesionResponse(Long id, String ipAddress, String userAgent, Instant fechaActividad) {

    public static AuditoriaSesionResponse from(AuditoriaSesion a) {
        return new AuditoriaSesionResponse(a.getId(), a.getIpAddress(), a.getUserAgent(), a.getFechaActividad());
    }
}
