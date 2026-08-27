package com.jventas.backend.compra;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Compra es el flujo transaccional más delicado del proyecto: mueve stock y
 * deja kardex de verdad. Se prueba de punta a punta contra Postgres real —
 * crear compra incrementa el stock, anularla lo revierte exactamente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class CompraFlowTests {

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
    void comprarIncrementaStockYAnularLoRevierte() {
        String token = login();

        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token);
        long productoId = crearProducto(token);

        // sin compras todavía: sin stock registrado
        assertThat(kardex(token, almacenId, productoId)).isEmpty();

        long compraId = registrarCompra(token, proveedorId, almacenId, productoId);

        var kardexTrasCompra = kardex(token, almacenId, productoId);
        assertThat(kardexTrasCompra).hasSize(1);
        assertThat(kardexTrasCompra.get(0).path("tipoDocumento").asText()).isEqualTo("COMPRA");
        assertThat(kardexTrasCompra.get(0).path("entrada").asInt()).isEqualTo(30);
        assertThat(kardexTrasCompra.get(0).path("stockResultante").asInt()).isEqualTo(30);

        anularCompra(token, compraId);

        var kardexTrasAnular = kardex(token, almacenId, productoId);
        assertThat(kardexTrasAnular).hasSize(2);
        assertThat(kardexTrasAnular.get(1).path("tipoDocumento").asText()).isEqualTo("PRODUCTO_ELIMINADO_COMPRA");
        assertThat(kardexTrasAnular.get(1).path("salida").asInt()).isEqualTo(30);
        assertThat(kardexTrasAnular.get(1).path("stockResultante").asInt()).isZero();

        // anular una segunda vez debe rechazarse, no duplicar la reversión
        restTestClient
                .post()
                .uri("/api/compras/{id}/anular", compraId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
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

    private long crearAlmacen(String token) {
        String body = """
                {"nombre":"Almacen Test","direccion":{"direccionLinea":"Av. Test 123"}}
                """;
        return postAndGetId(token, "/api/almacenes", body);
    }

    private long crearProveedor(String token) {
        String body = """
                {"ruc":"20999999999","razonSocial":"Proveedor Test","direccion":{"direccionLinea":"Calle Test 456"}}
                """;
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearProducto(String token) {
        String body = """
                {
                  "codigoBarras": "7509999999999",
                  "codigo": "FLOW-001",
                  "nombre": "Producto de flujo",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """;
        return postAndGetId(token, "/api/productos", body);
    }

    private long registrarCompra(String token, long proveedorId, long almacenId, long productoId) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "numeroDocumento": "F001-TEST",
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 30, "precioUnitario": 10.00, "impuestoId": 1}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        return postAndGetId(token, "/api/compras", body);
    }

    private void anularCompra(String token, long compraId) {
        restTestClient
                .post()
                .uri("/api/compras/{id}/anular", compraId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();
    }

    private List<JsonNode> kardex(String token, long almacenId, long productoId) {
        var node = restTestClient
                .get()
                .uri("/api/kardex?almacenId={almacenId}&productoId={productoId}", almacenId, productoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        List<JsonNode> lista = new ArrayList<>();
        node.path("content").forEach(lista::add);
        return lista;
    }

    private long postAndGetId(String token, String uri, String body) {
        var node = restTestClient
                .post()
                .uri(uri)
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
}
