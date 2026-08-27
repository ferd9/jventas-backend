package com.jventas.backend.proveedor;

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

/** Cubre desactivar/reactivar proveedor -- mismo gap que en cliente: sin esto, un proveedor no se podía dar de baja. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ProveedorEstadoFlowTests {

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
        long proveedorId = crearProveedor(token);

        assertThat(listarActivos(token)).anyMatch(p -> p.path("id").asLong() == proveedorId);

        restTestClient
                .post()
                .uri("/api/proveedores/{id}/desactivar", proveedorId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(listarActivos(token)).noneMatch(p -> p.path("id").asLong() == proveedorId);

        restTestClient
                .post()
                .uri("/api/proveedores/{id}/reactivar", proveedorId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(listarActivos(token)).anyMatch(p -> p.path("id").asLong() == proveedorId);
    }

    private java.util.List<JsonNode> listarActivos(String token) {
        var node = restTestClient
                .get()
                .uri("/api/proveedores?size=200")
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

    private long crearProveedor(String token) {
        String body = """
                {"ruc":"20777777777","razonSocial":"Proveedor De Estado","direccion":{"direccionLinea":"Calle 1"}}
                """;
        var node = restTestClient
                .post()
                .uri("/api/proveedores")
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
