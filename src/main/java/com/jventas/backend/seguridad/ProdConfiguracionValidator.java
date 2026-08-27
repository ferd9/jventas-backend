package com.jventas.backend.seguridad;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * "${CORS_ALLOWED_ORIGINS}" sin default en application-prod.yml NO falla el
 * arranque si la variable de entorno no existe -- Spring deja el
 * placeholder sin resolver como texto literal en vez de lanzar una
 * excepción (comprobado en la práctica, no es el comportamiento que uno
 * esperaría). Sin esta validación explícita, un despliegue sin
 * CORS_ALLOWED_ORIGINS arrancaría "bien" pero bloquearía todo origen real
 * en silencio, hasta que alguien lo notara desde el frontend.
 *
 * (La misma validación para JWT_SECRET vive en JwtService, no acá -- tiene
 * que correr en el constructor de ese bean para garantizar que se ejecuta
 * antes de construir la clave, sin depender del orden de creación de
 * beans de Spring.)
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdConfiguracionValidator {

    private final CorsProperties corsProperties;

    @PostConstruct
    void validar() {
        if (corsProperties.allowedOrigins().isEmpty()
                || corsProperties.allowedOrigins().stream().anyMatch(this::sinResolver)) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS no está definido en este entorno de producción. Sin esto, "
                            + "el frontend real no podrá llamar a la API.");
        }
    }

    private boolean sinResolver(String valor) {
        return valor == null || valor.isBlank() || valor.contains("${");
    }
}
