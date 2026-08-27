package com.jventas.backend.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unitario, sin contexto de Spring -- lo que se prueba acá es el chequeo de
 * "${JWT_SECRET} no se resolvió" del constructor, no requiere levantar nada.
 */
class JwtServiceTests {

    @Test
    void secretoInvalidoEnProdRevientaElArranque() {
        JwtProperties properties = new JwtProperties("", 30, 7);
        MockEnvironment prod = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(properties, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void placeholderSinResolverEnProdTambienRevienta() {
        // el caso real que motivó el chequeo: application-prod.yml sin la variable de entorno
        // deja el texto literal del placeholder en vez de fallar la resolución.
        JwtProperties properties = new JwtProperties("${JWT_SECRET}", 30, 7);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(properties, prod)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretoInvalidoFueraDeProdGeneraClaveAleatoriaSinFallar() {
        JwtProperties properties = new JwtProperties("", 30, 7);
        MockEnvironment sinPerfil = new MockEnvironment();

        JwtService service = new JwtService(properties, sinPerfil);
        String token = service.generar("usuario-test", java.util.List.of("producto:ver"));

        assertThat(token).isNotBlank();
    }

    @Test
    void secretoValidoEnProdFuncionaNormal() {
        JwtProperties properties = new JwtProperties("una-clave-de-prueba-suficientemente-larga-para-hs256", 30, 7);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        JwtService service = new JwtService(properties, prod);
        String token = service.generar("usuario-test", java.util.List.of("producto:ver"));

        assertThat(token).isNotBlank();
    }
}
