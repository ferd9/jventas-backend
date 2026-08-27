package com.jventas.backend.devolucion;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.compra.Compra;
import com.jventas.backend.compra.CompraRepository;
import com.jventas.backend.seguridad.LoginResponse;
import java.time.LocalDate;
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

/** Espejo de DevolucionFlowTests, del lado de compra a proveedor -- mismo plazo de 10 días. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class DevolucionCompraFlowTests {

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

    @Autowired
    private CompraRepository compraRepository;

    @Test
    void devolverDentroDelPlazoDecrementaStockYReduceElTotalDeLaCompra() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long productoId = crearProducto(token, "1");
        long compraId = registrarCompra(token, almacenId, productoId, "1", 20);
        long detalleCompraId = obtenerDetalleCompraId(token, compraId);

        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(20);
        var compraAntes = obtenerCompra(token, compraId);
        var totalAntes = compraAntes.path("total").decimalValue();

        var devolucion = registrarDevolucion(token, compraId, detalleCompraId, 5, "producto defectuoso");
        assertThat(devolucion.path("montoTotal").decimalValue()).isPositive();

        // stock baja: 20 - 5 = 15 -- vuelve al proveedor
        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(15);

        var compraDespues = obtenerCompra(token, compraId);
        assertThat(compraDespues.path("total").decimalValue()).isLessThan(totalAntes);
    }

    @Test
    void devolverLoSuficienteParaSaldarLaCompraLaPasaACancelado() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long productoId = crearProducto(token, "2");
        // 10 unidades a 10.00 = 100.00 de total, sin impuesto
        long compraId = registrarCompra(token, almacenId, productoId, "2", 10);
        long detalleCompraId = obtenerDetalleCompraId(token, compraId);

        registrarPago(token, compraId, "50.00");
        assertThat(obtenerCompra(token, compraId).path("estado").asText()).isEqualTo("PENDIENTE");

        // devolver 5 unidades (50.00) deja total=50, saldo = 50 - 50 = 0
        registrarDevolucion(token, compraId, detalleCompraId, 5, "saldar con devolución");

        assertThat(obtenerCompra(token, compraId).path("estado").asText()).isEqualTo("CANCELADO");
    }

    @Test
    void devolverMasDeLoDisponibleEntreDosDevolucionesSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long productoId = crearProducto(token, "3");
        long compraId = registrarCompra(token, almacenId, productoId, "3", 20);
        long detalleCompraId = obtenerDetalleCompraId(token, compraId);

        registrarDevolucion(token, compraId, detalleCompraId, 12, null);

        // ya se devolvieron 12 de 20 -- pedir 10 más excede lo que queda (8)
        restTestClient
                .post()
                .uri("/api/compras/{compraId}/devoluciones", compraId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(devolucionJson(detalleCompraId, 10, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void devolverMasDeLoQueQuedaEnStockSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token);
        long productoId = crearProducto(token, "4");
        long compraId = registrarCompra(token, almacenId, productoId, "4", 20);
        long detalleCompraId = obtenerDetalleCompraId(token, compraId);

        // se vendieron 18 de las 20 -- solo quedan 2 físicamente en el almacén
        registrarVenta(token, clienteId, almacenId, productoId, 18);
        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(2);

        // aunque la línea de compra "permite" devolver hasta 20, ya no hay stock físico para devolver 5
        restTestClient
                .post()
                .uri("/api/compras/{compraId}/devoluciones", compraId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(devolucionJson(detalleCompraId, 5, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void devolverDespuesDelPlazoDeDiezDiasSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long productoId = crearProducto(token, "5");
        long compraId = registrarCompra(token, almacenId, productoId, "5", 20);
        long detalleCompraId = obtenerDetalleCompraId(token, compraId);

        // la API no expone la fecha de compra -- se retrasa directo en la base para probar el plazo vencido
        Compra compra = compraRepository.findById(compraId).orElseThrow();
        compra.setFecha(LocalDate.now().minusDays(11));
        compraRepository.save(compra);

        restTestClient
                .post()
                .uri("/api/compras/{compraId}/devoluciones", compraId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(devolucionJson(detalleCompraId, 1, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private void registrarPago(String token, long compraId, String monto) {
        String body = """
                {"compraId": %d, "metodoPagoId": 1, "monto": %s}
                """.formatted(compraId, monto);
        restTestClient
                .post()
                .uri("/api/pagos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode registrarDevolucion(String token, long compraId, long detalleCompraId, int cantidad, String motivo) {
        var node = restTestClient
                .post()
                .uri("/api/compras/{compraId}/devoluciones", compraId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(devolucionJson(detalleCompraId, cantidad, motivo))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED)
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        return node;
    }

    private String devolucionJson(long detalleCompraId, int cantidad, String motivo) {
        String motivoJson = motivo == null ? "null" : "\"" + motivo + "\"";
        return """
                {"motivo": %s, "lineas": [{"detalleCompraId": %d, "cantidad": %d}]}
                """.formatted(motivoJson, detalleCompraId, cantidad);
    }

    private long obtenerDetalleCompraId(String token, long compraId) {
        return obtenerCompra(token, compraId).path("detalles").get(0).path("id").asLong();
    }

    private JsonNode obtenerCompra(String token, long compraId) {
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
        assertThat(node).isNotNull();
        return node;
    }

    private int stockActual(String token, long almacenId, long productoId) {
        var node = restTestClient
                .get()
                .uri("/api/stock?almacenId={almacenId}", almacenId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        List<JsonNode> lista = new java.util.ArrayList<>();
        node.forEach(lista::add);
        return lista.stream()
                .filter(s -> s.path("productoId").asLong() == productoId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sin fila de stock para el producto " + productoId))
                .path("cantidadActual")
                .asInt();
    }

    private void registrarVenta(String token, long clienteId, long almacenId, long productoId, int cantidad) {
        String body = """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 20.00}]
                }
                """.formatted(clienteId, almacenId, productoId, cantidad);
        postAndGetId(token, "/api/ventas", body);
    }

    private long registrarCompra(String token, long almacenId, long productoId, String sufijo, int cantidad) {
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"2088888888%s","razonSocial":"Proveedor Devolucion Compra","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(sufijo));
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 10.00}]
                }
                """.formatted(proveedorId, almacenId, productoId, cantidad);
        return postAndGetId(token, "/api/compras", body);
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
                {"nombre":"Almacen Devolucion Compra","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearCliente(String token) {
        return postAndGetId(token, "/api/clientes", """
                {"dni":"77766655","nombre":"Cliente","apellidos":"DevolucionCompra","tipo":"NATURAL"}
                """);
    }

    private long crearProducto(String token, String sufijo) {
        String body = """
                {
                  "codigoBarras": "753000000%s",
                  "codigo": "DEVOLC-%s",
                  "nombre": "Producto de devolución a proveedor",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 20}]
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
