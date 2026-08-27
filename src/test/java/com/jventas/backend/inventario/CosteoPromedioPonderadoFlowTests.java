package com.jventas.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import java.math.BigDecimal;
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
 * Decisión de negocio confirmada: costeo por promedio ponderado (no PEPS ni
 * UEPS). Cubre que comprar recalcula producto.costo, que vender/trasladar
 * solo lo leen (sin modificarlo), y que anular una compra reciente lo
 * revierte con precisión matemática -- caso "limpio", sin otra compra ni
 * venta de por medio, que es donde el promedio ponderado sí puede
 * reconstruirse exacto (ver CosteoPromedioPonderadoService para el límite
 * conocido cuando hay transacciones intermedias).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class CosteoPromedioPonderadoFlowTests {

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
    void dosComprasRecalculanElPromedioYVenderSoloLoLee() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "1");
        long clienteId = crearCliente(token, "1");
        long productoId = crearProducto(token, "COSTEO-001");

        // primera compra: sin stock previo, el costo pasa a ser directamente el de esta compra
        registrarCompra(token, proveedorId, almacenId, productoId, 100, "10.00");
        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("10.000");

        // segunda compra a otro precio: (100*10.00 + 50*16.00) / 150 = 12.00
        registrarCompra(token, proveedorId, almacenId, productoId, 50, "16.00");
        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("12.000");

        // vender no cambia el promedio -- solo lo lee para el kardex
        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 10, "25.00");
        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("12.000");

        var filaKardex = filaKardexPorTipo(token, almacenId, productoId, "VENTA");
        assertThat(filaKardex.path("costoUnitario").decimalValue()).isEqualByComparingTo("12.000");
        assertThat(filaKardex.path("costoTotal").decimalValue()).isEqualByComparingTo("120.00");
        // el costo (12.00) no es lo que se cobró (25.00) -- son cosas distintas a propósito
        assertThat(filaKardex.path("precio").decimalValue()).isEqualByComparingTo("25.00");

        assertThat(ventaId).isPositive();
    }

    @Test
    void anularUnaCompraRecienteRevierteElPromedioConPrecision() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "2");
        long productoId = crearProducto(token, "COSTEO-002");

        registrarCompra(token, proveedorId, almacenId, productoId, 100, "10.00");
        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("10.000");

        long compra2Id = registrarCompra(token, proveedorId, almacenId, productoId, 50, "16.00");
        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("12.000");

        // sin nada más de por medio, anular la reciente reconstruye el promedio exacto de antes
        restTestClient
                .post()
                .uri("/api/compras/{id}/anular", compra2Id)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(obtenerCostoProducto(token, productoId)).isEqualByComparingTo("10.000");
    }

    private BigDecimal obtenerCostoProducto(String token, long productoId) {
        var node = restTestClient
                .get()
                .uri("/api/productos/{id}", productoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        return node.path("costo").decimalValue();
    }

    private JsonNode filaKardexPorTipo(String token, long almacenId, long productoId, String tipoDocumento) {
        var node = restTestClient
                .get()
                .uri("/api/kardex?almacenId={almacenId}&productoId={productoId}&size=200", almacenId, productoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        for (JsonNode fila : node.path("content")) {
            if (fila.path("tipoDocumento").asText().equals(tipoDocumento)) {
                return fila;
            }
        }
        throw new AssertionError("No se encontró fila de kardex con tipoDocumento=" + tipoDocumento);
    }

    private long registrarCompra(String token, long proveedorId, long almacenId, long productoId, int cantidad, String precioUnitario) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": %s}]
                }
                """.formatted(proveedorId, almacenId, productoId, cantidad, precioUnitario);
        return postAndGetId(token, "/api/compras", body);
    }

    private long registrarVenta(String token, long clienteId, long almacenId, long productoId, int cantidad, String precioUnitario) {
        String body = """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": %s}]
                }
                """.formatted(clienteId, almacenId, productoId, cantidad, precioUnitario);
        return postAndGetId(token, "/api/ventas", body);
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
                {"nombre":"Almacen Costeo","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearProveedor(String token, String sufijo) {
        String body = """
                {"ruc":"2022222222%s","razonSocial":"Proveedor Costeo","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearCliente(String token, String sufijo) {
        String body = """
                {"dni":"2222222%s","nombre":"Cliente","apellidos":"Costeo","tipo":"NATURAL"}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/clientes", body);
    }

    private long crearProducto(String token, String codigo) {
        String body = """
                {
                  "codigoBarras": "750%s",
                  "codigo": "%s",
                  "nombre": "Producto de costeo",
                  "costo": 1,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigo, codigo);
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
