package com.jventas.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Levanta el contexto completo (JPA + Flyway + Security) contra un Postgres
 * real en un contenedor descartable. Si una migración no corre, una entidad
 * no calza con el esquema, o falta un bean de seguridad, esto falla — no
 * hace falta descubrirlo a mano contra el entorno local.
 */
@SpringBootTest
@Testcontainers
class BackendApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Test
    void elContextoArrancaYLasMigracionesCorren() {
        // si el contexto no levanta, JUnit lo reporta solo
    }
}
