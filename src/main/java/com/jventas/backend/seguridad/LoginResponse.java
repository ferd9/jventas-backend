package com.jventas.backend.seguridad;

import java.util.List;

public record LoginResponse(
        String token,
        String tokenType,
        long expiraEnSegundos,
        String refreshToken,
        String login,
        String nombreCompleto,
        List<String> authorities) {
}
