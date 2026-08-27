package com.jventas.backend.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
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
import tools.jackson.databind.JsonNode;

/** Cubre desactivar/reactivar cliente -- antes de esto, un cliente creado quedaba activo para siempre sin forma de darlo de baja. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ClienteEstadoFlowTests {

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
    void desactivarSacaDelListadoYReactivarLoDevuelve() {
        String token = login();
        long clienteId = crearCliente(token);

        assertThat(listarActivos(token)).anyMatch(c -> c.path("id").asLong() == clienteId);

        restTestClient
                .post()
                .uri("/api/clientes/{id}/desactivar", clienteId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(listarActivos(token)).noneMatch(c -> c.path("id").asLong() == clienteId);
        // sigue existiendo, solo inactivo -- obtener por id no filtra por activo
        restTestClient
                .get()
                .uri("/api/clientes/{id}", clienteId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();

        restTestClient
                .post()
                .uri("/api/clientes/{id}/reactivar", clienteId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(listarActivos(token)).anyMatch(c -> c.path("id").asLong() == clienteId);
    }

    private java.util.List<JsonNode> listarActivos(String token) {
        var node = restTestClient
                .get()
                .uri("/api/clientes?size=200")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        java.util.List<JsonNode> lista = new java.util.ArrayList<>();
        node.path("content").forEach(lista::add);
        return lista;
    }

    private long crearCliente(String token) {
        String body = """
                {"dni":"55566677","nombre":"Cliente","apellidos":"De Estado","tipo":"NATURAL"}
                """;
        var node = restTestClient
                .post()
                .uri("/api/clientes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED)
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        return node.path("id").asLong();
    }

    private String login() {
        String body = """
                {"login":"admin","password":"%s"}
                """.formatted(ADMIN_PASSWORD);
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
        return response.token();
    }
}
