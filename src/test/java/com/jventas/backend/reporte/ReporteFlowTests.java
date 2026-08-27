package com.jventas.backend.reporte;

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

/** Cubre el reporte de cuentas por pagar/cobrar y los filtros de fecha en /api/compras y /api/ventas. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ReporteFlowTests {

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
    void compraPendienteApareceEnCuentasPorPagarConSaldoCorrecto() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "20888888881");
        long productoId = crearProducto(token, "REPORTE-001");

        // total = 30 * 10.00 = 300.00 (sin impuesto)
        long compraId = registrarCompra(token, proveedorId, almacenId, productoId);

        var reporteInicial = cuentasPorPagar(token);
        var filaInicial = buscarPorCompraId(reporteInicial, compraId);
        assertThat(filaInicial.path("total").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(filaInicial.path("pagado").decimalValue()).isEqualByComparingTo("0");
        assertThat(filaInicial.path("saldo").decimalValue()).isEqualByComparingTo("300.00");

        registrarPago(token, "compraId", compraId, "100.00");

        var reporteTrasPago = cuentasPorPagar(token);
        var filaTrasPago = buscarPorCompraId(reporteTrasPago, compraId);
        assertThat(filaTrasPago.path("pagado").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(filaTrasPago.path("saldo").decimalValue()).isEqualByComparingTo("200.00");

        // pagar el saldo completo pasa la compra a CANCELADO (pagado) -- desaparece del reporte de pendientes
        registrarPago(token, "compraId", compraId, "200.00");
        var reporteFinal = cuentasPorPagar(token);
        assertThat(reporteFinal.stream().noneMatch(fila -> fila.path("compraId").asLong() == compraId)).isTrue();
    }

    @Test
    void filtroDeFechaEnListadoDeComprasExcluyeFueraDeRango() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "20888888882");
        long productoId = crearProducto(token, "REPORTE-002");
        registrarCompra(token, proveedorId, almacenId, productoId);

        // filtro que no incluye hoy -- no debe aparecer ninguna compra
        var vacio = restTestClient
                .get()
                .uri("/api/compras?fechaDesde=2000-01-01&fechaHasta=2000-01-02")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(vacio.path("content")).isEmpty();

        // filtro que sí incluye hoy -- debe aparecer
        var conResultado = restTestClient
                .get()
                .uri("/api/compras?fechaDesde=2000-01-01")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(conResultado.path("content")).isNotEmpty();
    }

    private JsonNode buscarPorCompraId(java.util.List<JsonNode> reporte, long compraId) {
        return reporte.stream()
                .filter(fila -> fila.path("compraId").asLong() == compraId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Compra " + compraId + " no aparece en el reporte"));
    }

    private java.util.List<JsonNode> cuentasPorPagar(String token) {
        var node = restTestClient
                .get()
                .uri("/api/reportes/cuentas-por-pagar")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        java.util.List<JsonNode> lista = new java.util.ArrayList<>();
        node.forEach(lista::add);
        return lista;
    }

    private void registrarPago(String token, String campoOrigen, long documentoId, String monto) {
        String body = """
                {"%s": %d, "metodoPagoId": 1, "monto": %s}
                """.formatted(campoOrigen, documentoId, monto);
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
                {"nombre":"Almacen Reporte","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearProveedor(String token, String ruc) {
        String body = """
                {"ruc":"%s","razonSocial":"Proveedor Reporte","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(ruc);
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearProducto(String token, String codigo) {
        String body = """
                {
                  "codigoBarras": "750111111%s",
                  "codigo": "%s",
                  "nombre": "Producto de reporte",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigo.substring(codigo.length() - 4), codigo);
        return postAndGetId(token, "/api/productos", body);
    }

    private long registrarCompra(String token, long proveedorId, long almacenId, long productoId) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "fechaVencimiento": "2030-01-01",
                  "detalles": [{"productoId": %d, "cantidad": 30, "precioUnitario": 10.00}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        return postAndGetId(token, "/api/compras", body);
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
