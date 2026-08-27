package com.jventas.backend.devolucion;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import com.jventas.backend.venta.Venta;
import com.jventas.backend.venta.VentaRepository;
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

/** Decisión de negocio confirmada: devolución parcial de venta, plazo de 10 días desde la fecha de venta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class DevolucionFlowTests {

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
    private VentaRepository ventaRepository;

    @Test
    void devolverDentroDelPlazoReingresaStockYReduceElTotalDeLaVenta() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "1");
        long productoId = crearProducto(token, "1");
        registrarCompra(token, almacenId, productoId, "1", 20);

        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 10);
        long detalleVentaId = obtenerDetalleVentaId(token, ventaId);

        // stock: 20 comprado - 10 vendido = 10
        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(10);
        var ventaAntes = obtenerVenta(token, ventaId);
        var totalAntes = ventaAntes.path("total").decimalValue();

        var devolucion = registrarDevolucion(token, ventaId, detalleVentaId, 3, "producto defectuoso");
        assertThat(devolucion.path("montoTotal").decimalValue()).isPositive();

        // stock vuelve: 10 + 3 = 13
        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(13);

        var ventaDespues = obtenerVenta(token, ventaId);
        assertThat(ventaDespues.path("total").decimalValue()).isLessThan(totalAntes);
    }

    @Test
    void devolverLoSuficienteParaSaldarLaVentaLaPasaACancelado() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "5");
        long productoId = crearProducto(token, "5");
        registrarCompra(token, almacenId, productoId, "5", 20);

        // 10 unidades a 20.00 = 200.00 de total, sin impuesto
        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 10);
        long detalleVentaId = obtenerDetalleVentaId(token, ventaId);

        // paga solo la mitad -- queda PENDIENTE con saldo 100
        registrarPago(token, ventaId, "100.00");
        assertThat(obtenerVenta(token, ventaId).path("estado").asText()).isEqualTo("PENDIENTE");

        // devolver 5 unidades (100.00) deja total=100, saldo = 100 - 100 = 0
        registrarDevolucion(token, ventaId, detalleVentaId, 5, "saldar con devolución");

        assertThat(obtenerVenta(token, ventaId).path("estado").asText()).isEqualTo("CANCELADO");
    }

    @Test
    void devolverMasDeLoDisponibleSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "2");
        long productoId = crearProducto(token, "2");
        registrarCompra(token, almacenId, productoId, "2", 20);

        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 5);
        long detalleVentaId = obtenerDetalleVentaId(token, ventaId);

        // primera devolución de 3 -- ok, queda margen de 2
        registrarDevolucion(token, ventaId, detalleVentaId, 3, null);

        // pedir 3 más (solo quedan 2 disponibles: 5 vendidos - 3 ya devueltos) se rechaza
        String body = devolucionJson(detalleVentaId, 3, null);
        restTestClient
                .post()
                .uri("/api/ventas/{ventaId}/devoluciones", ventaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void devolverDespuesDelPlazoDeDiezDiasSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token, "3");
        long productoId = crearProducto(token, "3");
        registrarCompra(token, almacenId, productoId, "3", 20);

        long ventaId = registrarVenta(token, clienteId, almacenId, productoId, 5);
        long detalleVentaId = obtenerDetalleVentaId(token, ventaId);

        // la API no expone la fecha de venta -- se retrasa directo en la base para probar el plazo vencido
        Venta venta = ventaRepository.findById(ventaId).orElseThrow();
        venta.setFecha(LocalDate.now().minusDays(11));
        ventaRepository.save(venta);

        String body = devolucionJson(detalleVentaId, 1, null);
        restTestClient
                .post()
                .uri("/api/ventas/{ventaId}/devoluciones", ventaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private void registrarPago(String token, long ventaId, String monto) {
        String body = """
                {"ventaId": %d, "metodoPagoId": 1, "monto": %s}
                """.formatted(ventaId, monto);
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

    private JsonNode registrarDevolucion(String token, long ventaId, long detalleVentaId, int cantidad, String motivo) {
        var node = restTestClient
                .post()
                .uri("/api/ventas/{ventaId}/devoluciones", ventaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(devolucionJson(detalleVentaId, cantidad, motivo))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED)
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        return node;
    }

    private String devolucionJson(long detalleVentaId, int cantidad, String motivo) {
        String motivoJson = motivo == null ? "null" : "\"" + motivo + "\"";
        return """
                {"motivo": %s, "lineas": [{"detalleVentaId": %d, "cantidad": %d}]}
                """.formatted(motivoJson, detalleVentaId, cantidad);
    }

    private long obtenerDetalleVentaId(String token, long ventaId) {
        var node = restTestClient
                .get()
                .uri("/api/ventas/{id}", ventaId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(node).isNotNull();
        return node.path("detalles").get(0).path("id").asLong();
    }

    private JsonNode obtenerVenta(String token, long ventaId) {
        var node = restTestClient
                .get()
                .uri("/api/ventas/{id}", ventaId)
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

    private long registrarVenta(String token, long clienteId, long almacenId, long productoId, int cantidad) {
        String body = """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 20.00}]
                }
                """.formatted(clienteId, almacenId, productoId, cantidad);
        return postAndGetId(token, "/api/ventas", body);
    }

    private void registrarCompra(String token, long almacenId, long productoId, String sufijo, int cantidad) {
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"2099999999%s","razonSocial":"Proveedor Devolucion","direccion":{"direccionLinea":"Calle 1"}}
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
                {"nombre":"Almacen Devolucion","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearCliente(String token, String sufijo) {
        String body = """
                {"dni":"666666%s","nombre":"Cliente","apellidos":"Devolucion","tipo":"NATURAL"}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/clientes", body);
    }

    private long crearProducto(String token, String sufijo) {
        String body = """
                {
                  "codigoBarras": "752000000%s",
                  "codigo": "DEVOL-%s",
                  "nombre": "Producto de devolución",
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
