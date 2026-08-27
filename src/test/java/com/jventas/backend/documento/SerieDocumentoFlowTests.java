package com.jventas.backend.documento;

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
 * El correlativo avanza uno por cada compra emitida con la serie — nunca
 * se salta ni se repite (misma transacción que la compra, con lock
 * pesimista sobre la fila de la serie).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class SerieDocumentoFlowTests {

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
    void cadaCompraConLaMismaSerieAvanzaElCorrelativoUnoAUno() {
        String token = login();

        long almacenId = crearAlmacen(token);
        long otroAlmacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token);
        long productoId = crearProducto(token);
        long serieId = crearSerie(token, almacenId, 1L, "F001");

        String numero1 = registrarCompraConSerie(token, proveedorId, almacenId, productoId, serieId);
        String numero2 = registrarCompraConSerie(token, proveedorId, almacenId, productoId, serieId);

        assertThat(numero1).isEqualTo("F001-00000001");
        assertThat(numero2).isEqualTo("F001-00000002");

        // la serie pertenece a `almacenId`, usarla desde otro almacén se rechaza
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "serieDocumentoId": %d,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 1, "precioUnitario": 1.00}]
                }
                """.formatted(serieId, proveedorId, otroAlmacenId, productoId);
        restTestClient
                .post()
                .uri("/api/compras")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
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
        return postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Serie","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearProveedor(String token) {
        return postAndGetId(token, "/api/proveedores", """
                {"ruc":"20555555555","razonSocial":"Proveedor Serie","direccion":{"direccionLinea":"Calle 1"}}
                """);
    }

    private long crearProducto(String token) {
        String body = """
                {
                  "codigoBarras": "7505555555555",
                  "codigo": "SERIE-001",
                  "nombre": "Producto de serie",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """;
        return postAndGetId(token, "/api/productos", body);
    }

    private long crearSerie(String token, long almacenId, long tipoDocumentoId, String serie) {
        String body = """
                {"almacenId": %d, "tipoDocumentoId": %d, "serie": "%s"}
                """.formatted(almacenId, tipoDocumentoId, serie);
        return postAndGetId(token, "/api/series-documento", body);
    }

    private String registrarCompraConSerie(String token, long proveedorId, long almacenId, long productoId, long serieId) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "serieDocumentoId": %d,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 1, "precioUnitario": 1.00}]
                }
                """.formatted(serieId, proveedorId, almacenId, productoId);
        var node = restTestClient
                .post()
                .uri("/api/compras")
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
        return node.path("numeroDocumento").asText();
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
