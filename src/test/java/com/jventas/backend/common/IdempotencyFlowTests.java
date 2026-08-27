package com.jventas.backend.common;

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

/** Cubre IdempotencyFilter: mismo Idempotency-Key en dos POST /api/ventas no crea dos ventas; sin el header, sí. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class IdempotencyFlowTests {

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
    void mismaClaveDeIdempotenciaDevuelveLaMismaVentaSinDuplicar() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "1");
        long productoId = crearProducto(token, "1");
        registrarCompra(token, almacenId, productoId, "1");

        String claveIdempotencia = "test-clave-" + java.util.UUID.randomUUID();
        String body = ventaJson(clienteId, almacenId, productoId);

        var primeraRespuesta = postVenta(token, body, claveIdempotencia);
        assertThat(primeraRespuesta.status()).isEqualTo(HttpStatus.CREATED.value());
        long primerId = primeraRespuesta.cuerpo().path("id").asLong();

        var segundaRespuesta = postVenta(token, body, claveIdempotencia);
        assertThat(segundaRespuesta.status()).isEqualTo(HttpStatus.CREATED.value());
        long segundoId = segundaRespuesta.cuerpo().path("id").asLong();

        // misma clave -- misma respuesta, no una venta nueva
        assertThat(segundoId).isEqualTo(primerId);
        assertThat(contarVentasDelCliente(token, clienteId)).isEqualTo(1);
    }

    @Test
    void sinClaveDeIdempotenciaCadaPostCreaUnaVentaNueva() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "2");
        long productoId = crearProducto(token, "2");
        registrarCompra(token, almacenId, productoId, "2");

        String body = ventaJson(clienteId, almacenId, productoId);

        postVenta(token, body, null);
        postVenta(token, body, null);

        assertThat(contarVentasDelCliente(token, clienteId)).isEqualTo(2);
    }

    private record RespuestaVenta(int status, JsonNode cuerpo) {}

    private RespuestaVenta postVenta(String token, String body, String claveIdempotencia) {
        var spec = restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
        if (claveIdempotencia != null) {
            spec = spec.header("Idempotency-Key", claveIdempotencia);
        }
        var resultado = spec.body(body).exchange().returnResult(JsonNode.class);
        return new RespuestaVenta(resultado.getStatus().value(), resultado.getResponseBody());
    }

    private int contarVentasDelCliente(String token, long clienteId) {
        var node = restTestClient
                .get()
                .uri("/api/ventas?clienteId={clienteId}", clienteId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        return node.path("content").size();
    }

    private String ventaJson(long clienteId, long almacenId, long productoId) {
        return """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 1, "precioUnitario": 10.00}]
                }
                """.formatted(clienteId, almacenId, productoId);
    }

    private void registrarCompra(String token, long almacenId, long productoId, String sufijo) {
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"2055555555%s","razonSocial":"Proveedor Idempotencia","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(sufijo));
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 50, "precioUnitario": 5.00}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        postAndGetId(token, "/api/compras", body);
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
                {"nombre":"Almacen Idempotencia","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearCliente(String token, String sufijo) {
        String body = """
                {"dni":"3334445%s","nombre":"Cliente","apellidos":"Idempotencia","tipo":"NATURAL"}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/clientes", body);
    }

    private long crearProducto(String token, String sufijo) {
        String body = """
                {
                  "codigoBarras": "750111111666%s",
                  "codigo": "IDEMP-00%s",
                  "nombre": "Producto de idempotencia",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(sufijo, sufijo);
        return postAndGetId(token, "/api/productos", body);
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
