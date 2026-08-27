package com.jventas.backend.serieproducto;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
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
 * Decisión de negocio confirmada: trazabilidad por serie para productos
 * cuya categoría lo exige (categoria.requiere_serie), captura de números al
 * comprar, selección manual del vendedor al vender.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class SerieProductoFlowTests {

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
    void comprarSinNumerosDeSerieSeRechaza() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "1");
        long productoId = crearProductoConSerie(token, "1");

        restTestClient
                .post()
                .uri("/api/compras")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(compraJson(proveedorId, almacenId, productoId, 2, null))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void flujoCompletoDeCompraVentaYAnulaciones() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = crearProveedor(token, "2");
        long clienteId = crearCliente(token, "2");
        long productoId = crearProductoConSerie(token, "2");

        long compraId = postAndGetId(
                token,
                "/api/compras",
                compraJson(proveedorId, almacenId, productoId, 2, List.of("SN-A-001", "SN-A-002")));

        var disponibles = seriesDisponibles(token, productoId, almacenId);
        assertThat(disponibles).hasSize(2);
        assertThat(disponibles.stream().map(n -> n.path("numeroSerie").asText())).containsExactlyInAnyOrder("SN-A-001", "SN-A-002");

        // vender una serie concreta, elegida a mano
        long ventaId = postAndGetId(
                token, "/api/ventas", ventaJson(clienteId, almacenId, productoId, 1, List.of("SN-A-001")));

        // ya no está disponible -- solo queda la otra
        var disponiblesTrasVenta = seriesDisponibles(token, productoId, almacenId);
        assertThat(disponiblesTrasVenta).hasSize(1);
        assertThat(disponiblesTrasVenta.get(0).path("numeroSerie").asText()).isEqualTo("SN-A-002");

        // vender la misma serie de nuevo se rechaza -- ya está vendida
        restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ventaJson(clienteId, almacenId, productoId, 1, List.of("SN-A-001")))
                .exchange()
                .expectStatus()
                .isBadRequest();

        // anular la venta libera la serie de nuevo
        restTestClient
                .post()
                .uri("/api/ventas/{id}/anular", ventaId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(seriesDisponibles(token, productoId, almacenId)).hasSize(2);

        // anular la compra completa desactiva ambas series -- ya no aparecen disponibles
        restTestClient
                .post()
                .uri("/api/compras/{id}/anular", compraId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(seriesDisponibles(token, productoId, almacenId)).isEmpty();
    }

    @Test
    void venderUnaSerieQueNoEstaEnEseAlmacenSeRechaza() {
        String token = login();
        long almacenOrigen = crearAlmacen(token);
        long almacenOtro = postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Ajeno Serie","direccion":{"direccionLinea":"Av. Otro 1"}}
                """);
        long proveedorId = crearProveedor(token, "3");
        long clienteId = crearCliente(token, "3");
        long productoId = crearProductoConSerie(token, "3");

        postAndGetId(token, "/api/compras", compraJson(proveedorId, almacenOrigen, productoId, 1, List.of("SN-B-001")));

        // la serie existe, pero está en almacenOrigen, no en almacenOtro
        restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ventaJson(clienteId, almacenOtro, productoId, 1, List.of("SN-B-001")))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void completarTrasladoMueveLasSeriesAlDestino() {
        String token = login();
        long origenId = crearAlmacen(token);
        long destinoId = postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Destino Serie","direccion":{"direccionLinea":"Av. Destino 1"}}
                """);
        long proveedorId = crearProveedor(token, "4");
        long productoId = crearProductoConSerie(token, "4");

        postAndGetId(token, "/api/compras", compraJson(proveedorId, origenId, productoId, 1, List.of("SN-C-001")));
        assertThat(seriesDisponibles(token, productoId, origenId)).hasSize(1);
        assertThat(seriesDisponibles(token, productoId, destinoId)).isEmpty();

        String trasladoBody = """
                {"almacenOrigenId": %d, "almacenDestinoId": %d, "detalles": [{"productoId": %d, "cantidad": 1}]}
                """.formatted(origenId, destinoId, productoId);
        long trasladoId = postAndGetId(token, "/api/traslados", trasladoBody);

        restTestClient
                .post()
                .uri("/api/traslados/{id}/completar", trasladoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(seriesDisponibles(token, productoId, origenId)).isEmpty();
        var enDestino = seriesDisponibles(token, productoId, destinoId);
        assertThat(enDestino).hasSize(1);
        assertThat(enDestino.get(0).path("numeroSerie").asText()).isEqualTo("SN-C-001");
    }

    private List<JsonNode> seriesDisponibles(String token, long productoId, long almacenId) {
        var node = restTestClient
                .get()
                .uri("/api/series-producto?productoId={productoId}&almacenId={almacenId}", productoId, almacenId)
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
        return lista;
    }

    private String compraJson(long proveedorId, long almacenId, long productoId, int cantidad, List<String> numerosSerie) {
        String numerosJson = numerosSerie == null ? "null" : numerosSerie.stream().map(n -> "\"" + n + "\"").reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");
        return """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 100.00, "numerosSerie": %s}]
                }
                """.formatted(proveedorId, almacenId, productoId, cantidad, numerosJson);
    }

    private String ventaJson(long clienteId, long almacenId, long productoId, int cantidad, List<String> numerosSerie) {
        String numerosJson = numerosSerie.stream().map(n -> "\"" + n + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
        return """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": %d, "precioUnitario": 150.00, "numerosSerie": %s}]
                }
                """.formatted(clienteId, almacenId, productoId, cantidad, numerosJson);
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
                {"nombre":"Almacen Serie","direccion":{"direccionLinea":"Av. Serie 1"}}
                """);
    }

    private long crearProveedor(String token, String sufijo) {
        String body = """
                {"ruc":"2011111111%s","razonSocial":"Proveedor Serie","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearCliente(String token, String sufijo) {
        String body = """
                {"dni":"1111111%s","nombre":"Cliente","apellidos":"Serie","tipo":"NATURAL"}
                """.formatted(sufijo);
        return postAndGetId(token, "/api/clientes", body);
    }

    private long crearProductoConSerie(String token, String sufijo) {
        long categoriaId = postAndGetId(token, "/api/categorias", """
                {"nombre":"Categoria Con Serie %s","requiereSerie":true}
                """.formatted(sufijo));
        String body = """
                {
                  "codigoBarras": "751%s",
                  "codigo": "SERIE-%s",
                  "nombre": "Producto con serie",
                  "costo": 50,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "categoriaId": %d,
                  "precios": [{"listaPrecioId": 1, "precio": 150}]
                }
                """.formatted(sufijo, sufijo, categoriaId);
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
