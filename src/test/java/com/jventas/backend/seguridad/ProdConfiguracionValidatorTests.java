package com.jventas.backend.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unitario, sin contexto de Spring -- @PostConstruct se llama directo, no requiere levantar nada. */
class ProdConfiguracionValidatorTests {

    @Test
    void sinOrigenesConfiguradosRevienta() {
        var validator = new ProdConfiguracionValidator(new CorsProperties(List.of()));

        assertThatThrownBy(validator::validar).isInstanceOf(IllegalStateException.class).hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void placeholderSinResolverRevienta() {
        var validator = new ProdConfiguracionValidator(new CorsProperties(List.of("${CORS_ALLOWED_ORIGINS}")));

        assertThatThrownBy(validator::validar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void origenesRealesNoRevientan() {
        var validator = new ProdConfiguracionValidator(new CorsProperties(List.of("https://app.jventas.example.com")));

        assertThatCode(validator::validar).doesNotThrowAnyException();
    }
}
