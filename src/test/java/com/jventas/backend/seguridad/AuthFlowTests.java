package com.jventas.backend.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Cubre refresh token (emisión/uso/revocación) y el rate limiter de login. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AuthFlowTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @DynamicPropertySource
    static void adminPassword(DynamicPropertyRegistry registry) {
        registry.add("jventas.bootstrap.admin.password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void refreshTokenEmiteAccesoNuevoYLogoutLoRevoca() {
        LoginResponse login = login("admin", ADMIN_PASSWORD);
        assertThat(login.refreshToken()).isNotBlank();

        // el refresh token, sin volver a pedir contraseña, entrega un access token nuevo
        LoginResponse refrescado = restTestClient
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"refreshToken":"%s"}
                        """.formatted(login.refreshToken()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(refrescado).isNotNull();
        assertThat(refrescado.token()).isNotBlank();

        // logout revoca el refresh token -- un refresh posterior con el mismo token falla
        restTestClient
                .post()
                .uri("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"refreshToken":"%s"}
                        """.formatted(login.refreshToken()))
                .exchange()
                .expectStatus()
                .isNoContent();

        restTestClient
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"refreshToken":"%s"}
                        """.formatted(login.refreshToken()))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshTokenInvalidoEsRechazado() {
        restTestClient
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"refreshToken":"token-que-no-existe"}
                        """)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tresIntentosFallidosSeguidosDeUnoCorrectoNoBloquean() {
        // registrarExito() limpia el contador -- 3 fallos (< MAX_INTENTOS=5) seguidos de éxito no deja bloqueo
        for (int i = 0; i < 3; i++) {
            restTestClient
                    .post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"login":"admin","password":"clave-incorrecta"}
                            """)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        LoginResponse login = login("admin", ADMIN_PASSWORD);
        assertThat(login.token()).isNotBlank();
    }

    @Test
    void cincoIntentosFallidosBloqueanElLogin() {
        for (int i = 0; i < 5; i++) {
            restTestClient
                    .post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"login":"admin-bloqueo","password":"clave-incorrecta"}
                            """)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // el sexto intento -- incluso con la contraseña correcta -- queda bloqueado por la ventana de 15 min
        restTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"login":"admin-bloqueo","password":"cualquier-cosa"}
                        """)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private LoginResponse login(String login, String password) {
        String body = """
                {"login":"%s","password":"%s"}
                """.formatted(login, password);
        LoginResponse response = restTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(response).isNotNull();
        return response;
    }
}
