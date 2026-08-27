package com.jventas.backend.traslado;

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
 * Traslado es de dos pasos, a propósito: crear() ya saca el stock del
 * origen (queda "en tránsito"); completar() recién lo suma al destino.
 * Se prueba el ciclo completo, y por separado que anular solo funciona
 * mientras está pendiente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class TrasladoFlowTests {

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
    void trasladarSacaDelOrigenYCompletarLoSumaAlDestino() {
        String token = login();

        long origenId = crearAlmacen(token, "Origen");
        long destinoId = crearAlmacen(token, "Destino");
        long proveedorId = crearProveedor(token, "20777777771");
        long productoId = crearProducto(token, "7507777777771", "TRASLADO-001");

        registrarCompra(token, proveedorId, origenId, productoId, 100);

        long trasladoId = registrarTraslado(token, origenId, destinoId, productoId, 40);

        // ya salió del origen, todavía no llegó al destino
        assertThat(stockActual(token, origenId, productoId)).isEqualTo(60);
        assertThat(stockActual(token, destinoId, productoId)).isZero();

        completarTraslado(token, trasladoId);

        assertThat(stockActual(token, destinoId, productoId)).isEqualTo(40);

        var kardexDestino = kardex(token, destinoId, productoId);
        assertThat(kardexDestino).hasSize(1);
        assertThat(kardexDestino.get(0).path("tipoDocumento").asText()).isEqualTo("TRASLADO_ENTRADA");

        // ya completado: ni anular ni volver a completar deben funcionar
        restTestClient
                .post()
                .uri("/api/traslados/{id}/anular", trasladoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
        restTestClient
                .post()
                .uri("/api/traslados/{id}/completar", trasladoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anularUnTrasladoPendienteDevuelveElStockAlOrigen() {
        String token = login();

        long origenId = crearAlmacen(token, "Origen Anular");
        long destinoId = crearAlmacen(token, "Destino Anular");
        long proveedorId = crearProveedor(token, "20777777772");
        long productoId = crearProducto(token, "7507777777772", "TRASLADO-002");

        registrarCompra(token, proveedorId, origenId, productoId, 50);
        long trasladoId = registrarTraslado(token, origenId, destinoId, productoId, 20);

        assertThat(stockActual(token, origenId, productoId)).isEqualTo(30);

        restTestClient
                .post()
                .uri("/api/traslados/{id}/anular", trasladoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(stockActual(token, origenId, productoId)).isEqualTo(50);
        assertThat(stockActual(token, destinoId, productoId)).isZero();
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

    private long crearAlmacen(String token, String nombre) {
        String body = """
                {"nombre":"%s","direccion":{"direccionLinea":"Av. Test 1"}}
                """.formatted(nombre);
        return postAndGetId(token, "/api/almacenes", body);
    }

    private long crearProveedor(String token, String ruc) {
        String body = """
                {"ruc":"%s","razonSocial":"Proveedor Traslado","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(ruc);
        return postAndGetId(token, "/api/proveedores", body);
    }

    private long crearProducto(String token, String codigoBarras, String codigo) {
        String body = """
                {
                  "codigoBarras": "%s",
                  "codigo": "%s",
                  "nombre": "Producto de traslado",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigoBarras, codigo);
        return postAndGetId(token, "/api/productos", body);
    }

    private void registrarCompra(String token, long proveedorId, long almacenId, long productoId, int cantidad) {
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

    private long registrarTraslado(String token, long origenId, long destinoId, long productoId, int cantidad) {
        String body = """
                {
                  "almacenOrigenId": %d,
                  "almacenDestinoId": %d,
                  "detalles": [{"productoId": %d, "cantidad": %d}]
                }
                """.formatted(origenId, destinoId, productoId, cantidad);
        return postAndGetId(token, "/api/traslados", body);
    }

    private void completarTraslado(String token, long trasladoId) {
        restTestClient
                .post()
                .uri("/api/traslados/{id}/completar", trasladoId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();
    }

    private int stockActual(String token, long almacenId, long productoId) {
        var lista = kardex(token, almacenId, productoId);
        if (lista.isEmpty()) {
            return 0;
        }
        return lista.get(lista.size() - 1).path("stockResultante").asInt();
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
