package com.jventas.backend.venta;

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
 * Venta es el espejo de Compra, con una diferencia real: sí valida stock
 * disponible antes de confirmar. Se compra stock primero (mismo pipeline
 * real que usaría un usuario) y luego se vende sobre él.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class VentaFlowTests {

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
    void venderDecrementaStockValidaDisponibleYAnularLoRevierte() {
        String token = login();

        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token);
        long clienteId = crearCliente(token);
        long productoId = crearProducto(token);

        // seed de stock: compra real de 100 unidades, mismo pipeline que usaría un usuario
        registrarCompra(token, proveedorId, almacenId, productoId, 100);

        // vender más de lo disponible se rechaza, sin tocar el stock
        rechazarVentaPorStockInsuficiente(token, clienteId, almacenId, productoId, 1000);

        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 30);

        var kardex = kardex(token, almacenId, productoId);
        assertThat(kardex).hasSize(2); // COMPRA (entrada 100) + VENTA (salida 30)
        assertThat(kardex.get(1).path("tipoDocumento").asText()).isEqualTo("VENTA");
        assertThat(kardex.get(1).path("salida").asInt()).isEqualTo(30);
        assertThat(kardex.get(1).path("stockResultante").asInt()).isEqualTo(70);

        anularVenta(token, ventaId);

        var kardexTrasAnular = kardex(token, almacenId, productoId);
        assertThat(kardexTrasAnular).hasSize(3);
        assertThat(kardexTrasAnular.get(2).path("tipoDocumento").asText()).isEqualTo("PRODUCTO_ELIMINADO_VENTA");
        assertThat(kardexTrasAnular.get(2).path("entrada").asInt()).isEqualTo(30);
        assertThat(kardexTrasAnular.get(2).path("stockResultante").asInt()).isEqualTo(100);
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
                {"nombre":"Almacen Venta Test","direccion":{"direccionLinea":"Av. Test 789"}}
                """);
    }

    private long crearProveedor(String token) {
        return postAndGetId(token, "/api/proveedores", """
                {"ruc":"20888888888","razonSocial":"Proveedor Venta Test","direccion":{"direccionLinea":"Calle Test 1"}}
                """);
    }

    private long crearCliente(String token) {
        return postAndGetId(token, "/api/clientes", """
                {"dni":"87654321","nombre":"Juan","apellidos":"Perez","tipo":"NATURAL"}
                """);
    }

    private long crearProducto(String token) {
        String body = """
                {
                  "codigoBarras": "7508888888888",
                  "codigo": "VENTA-FLOW-001",
                  "nombre": "Producto de venta",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 20}]
                }
                """;
        return postAndGetId(token, "/api/productos", body);
    }

    private void registrarCompra(String token, long proveedorId, long almacenId, long productoId, int cantidad) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "numeroDocumento": "F001-SEED",
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 10.00}]
                }
                """.formatted(proveedorId, almacenId, productoId, cantidad);
        postAndGetId(token, "/api/compras", body);
    }

    private void rechazarVentaPorStockInsuficiente(String token, long clienteId, long almacenId, long productoId, int cantidad) {
        String body = ventaJson(clienteId, almacenId, productoId, cantidad);
        restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private long registrarVenta(String token, long clienteId, long almacenId, long productoId, int cantidad) {
        return postAndGetId(token, "/api/ventas", ventaJson(clienteId, almacenId, productoId, cantidad));
    }

    private String ventaJson(long clienteId, long almacenId, long productoId, int cantidad) {
        return """
                {
                  "tipoDocumentoId": 2,
                  "numeroDocumento": "B001-TEST",
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 20.00}]
                }
                """.formatted(clienteId, almacenId, productoId, cantidad);
    }

    private void anularVenta(String token, long ventaId) {
        restTestClient
                .post()
                .uri("/api/ventas/{id}/anular", ventaId)
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
