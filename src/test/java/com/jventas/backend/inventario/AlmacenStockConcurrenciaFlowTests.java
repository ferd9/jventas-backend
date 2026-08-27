package com.jventas.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Prueba con concurrencia real (hilos + WebEnvironment.RANDOM_PORT, no
 * mocks) que AlmacenStockRepository.findByAlmacenIdAndProductoIdParaActualizar
 * evita el lost update: N ventas de 1 unidad disparadas en paralelo contra
 * el mismo producto/almacén deben descontar exactamente N, ni más ni
 * menos -- antes del @Lock, esto perdía actualizaciones al azar bajo carga.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AlmacenStockConcurrenciaFlowTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final int STOCK_INICIAL = 50;
    private static final int VENTAS_CONCURRENTES = 15;

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
    void ventasConcurrentesNoPierdenActualizacionesDeStock() throws Exception {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = crearCliente(token);
        long productoId = crearProducto(token);
        long proveedorId = crearProveedor(token);
        registrarCompra(token, proveedorId, almacenId, productoId, STOCK_INICIAL);

        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(STOCK_INICIAL);

        ExecutorService pool = Executors.newFixedThreadPool(VENTAS_CONCURRENTES);
        try {
            List<Callable<Integer>> tareas = java.util.stream.IntStream.range(0, VENTAS_CONCURRENTES)
                    .<Callable<Integer>>mapToObj(i -> () -> venderUnaUnidad(token, clienteId, almacenId, productoId))
                    .toList();

            List<Future<Integer>> resultados = pool.invokeAll(tareas);
            List<Integer> statusCodes = new java.util.ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                statusCodes.add(resultado.get());
            }

            long exitosas = statusCodes.stream().filter(s -> s == HttpStatus.CREATED.value()).count();
            assertThat(exitosas).isEqualTo(VENTAS_CONCURRENTES);
            assertThat(statusCodes).containsOnly(HttpStatus.CREATED.value());
        } finally {
            pool.shutdown();
        }

        // si hubiera lost updates, este número sería mayor a STOCK_INICIAL - VENTAS_CONCURRENTES
        assertThat(stockActual(token, almacenId, productoId)).isEqualTo(STOCK_INICIAL - VENTAS_CONCURRENTES);
    }

    private int venderUnaUnidad(String token, long clienteId, long almacenId, long productoId) {
        String body = """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 1, "precioUnitario": 10.00}]
                }
                """.formatted(clienteId, almacenId, productoId);
        return restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .returnResult(JsonNode.class)
                .getStatus()
                .value();
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
        List<JsonNode> lista = new java.util.ArrayList<>();
        node.forEach(lista::add);
        return lista.stream()
                .filter(s -> s.path("productoId").asLong() == productoId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sin fila de stock para el producto " + productoId))
                .path("cantidadActual")
                .asInt();
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
                {"nombre":"Almacen Concurrencia","direccion":{"direccionLinea":"Av. Test 1"}}
                """);
    }

    private long crearCliente(String token) {
        return postAndGetId(token, "/api/clientes", """
                {"dni":"44455566","nombre":"Cliente","apellidos":"Concurrencia","tipo":"NATURAL"}
                """);
    }

    private long crearProveedor(String token) {
        return postAndGetId(token, "/api/proveedores", """
                {"ruc":"20666666666","razonSocial":"Proveedor Concurrencia","direccion":{"direccionLinea":"Calle 1"}}
                """);
    }

    private long crearProducto(String token) {
        String body = """
                {
                  "codigoBarras": "7501111117777",
                  "codigo": "CONC-001",
                  "nombre": "Producto de concurrencia",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """;
        return postAndGetId(token, "/api/productos", body);
    }

    private void registrarCompra(String token, long proveedorId, long almacenId, long productoId, int cantidad) {
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 5.00}]
                }
                """.formatted(proveedorId, almacenId, productoId, cantidad);
        postAndGetId(token, "/api/compras", body);
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
