package com.jventas.backend.pago;

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

/**
 * Pago admite montos parciales y actualiza compra/venta a CANCELADO (=
 * pagado) solo cuando el saldo llega a 0 — nunca antes, y nunca permite
 * pagar de más.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class PagoFlowTests {

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
    void pagosParcialesLiquidanLaCompraSoloAlLlegarASaldoCero() {
        String token = login();

        long proveedorId = crearProveedor(token, "20666666661");
        long almacenId = crearAlmacen(token, "Almacen Pago 1");
        long productoId = crearProducto(token, "7506666666661", "PAGO-001");
        long compraId = registrarCompra(token, proveedorId, almacenId, productoId); // total = 590.00 (500 + 18% IGV)

        assertThat(saldo(token, compraId).path("saldo").asDouble()).isEqualTo(590.00);
        assertThat(estadoCompra(token, compraId)).isEqualTo("PENDIENTE");

        pagar(token, compraId, 300.00, HttpStatus.CREATED);
        assertThat(saldo(token, compraId).path("saldo").asDouble()).isEqualTo(290.00);
        assertThat(estadoCompra(token, compraId)).isEqualTo("PENDIENTE");

        // pagar más de lo que queda de saldo se rechaza, no deja el pago a medias
        pagar(token, compraId, 291.00, HttpStatus.BAD_REQUEST);
        assertThat(saldo(token, compraId).path("saldo").asDouble()).isEqualTo(290.00);

        pagar(token, compraId, 290.00, HttpStatus.CREATED);
        assertThat(saldo(token, compraId).path("saldo").asDouble()).isZero();
        assertThat(estadoCompra(token, compraId)).isEqualTo("CANCELADO");
    }

    @Test
    void pagoConAmbosOrigenesORespectivoNingunoSeRechaza() {
        String token = login();
        long proveedorId = crearProveedor(token, "20666666662");
        long almacenId = crearAlmacen(token, "Almacen Pago 2");
        long productoId = crearProducto(token, "7506666666662", "PAGO-002");
        long compraId = registrarCompra(token, proveedorId, almacenId, productoId);

        // ni compraId ni ventaId
        String sinOrigen = """
                {"metodoPagoId": 1, "monto": 50.00}
                """;
        restTestClient
                .post()
                .uri("/api/pagos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(sinOrigen)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // ambos a la vez
        String ambos = """
                {"compraId": %d, "ventaId": 999, "metodoPagoId": 1, "monto": 50.00}
                """.formatted(compraId);
        restTestClient
                .post()
                .uri("/api/pagos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ambos)
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

    private long crearProveedor(String token, String ruc) {
        String body = """
                {"ruc":"%s","razonSocial":"Proveedor Pago","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(ruc);
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearAlmacen(String token, String nombre) {
        String body = """
                {"nombre":"%s","direccion":{"direccionLinea":"Av. Test 1"}}
                """.formatted(nombre);
        return postAndGetId(token, "/api/almacenes", body);
    }

    private long crearProducto(String token, String codigoBarras, String codigo) {
        String body = """
                {
                  "codigoBarras": "%s",
                  "codigo": "%s",
                  "nombre": "Producto de pago",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigoBarras, codigo);
        return postAndGetId(token, "/api/productos", body);
    }

    private long registrarCompra(String token, long proveedorId, long almacenId, long productoId) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 50, "precioUnitario": 10.00, "impuestoId": 1}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        return postAndGetId(token, "/api/compras", body);
    }

    private void pagar(String token, long compraId, double monto, HttpStatus esperado) {
        String body = """
                {"compraId": %d, "metodoPagoId": 1, "monto": %s, "referencia": "test"}
                """.formatted(compraId, monto);
        restTestClient
                .post()
                .uri("/api/pagos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(esperado);
    }

    private JsonNode saldo(String token, long compraId) {
        return restTestClient
                .get()
                .uri("/api/pagos/saldo?compraId={compraId}", compraId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
    }

    private String estadoCompra(String token, long compraId) {
        var node = restTestClient
                .get()
                .uri("/api/compras/{id}", compraId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        return node.path("estado").asText();
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
