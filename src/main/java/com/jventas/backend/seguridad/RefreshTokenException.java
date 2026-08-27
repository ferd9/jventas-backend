package com.jventas.backend.seguridad;

import org.springframework.security.core.AuthenticationException;

/**
 * Subclase propia (en vez de reutilizar BadCredentialsException) para que
 * el mensaje real ("expirado", "revocado", "usuario inactivo") le llegue
 * al cliente — el handler genérico de AuthenticationException lo pisa con
 * un "usuario o contraseña incorrectos" genérico, correcto para login,
 * pero incorrecto acá.
 */
public class RefreshTokenException extends AuthenticationException {

    public RefreshTokenException(String message) {
        super(message);
    }
}
