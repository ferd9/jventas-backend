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

/**
 * Cubre que los listados (producto, venta, compra, traslado, usuario) siguen
 * trayendo los nombres de sus relaciones (categoría, marca, cliente,
 * proveedor, almacén origen/destino, cargo) después de convertir esos
 * repositorios a `join fetch` -- no solo que el fetch join compile, sino que
 * el campo realmente llegue poblado en la respuesta. Verificado a mano por
 * fuera de este test que además cae a una sola query (antes: una por fila
 * de la página, por cada asociación perezosa leída).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ListadosEnriquecidosFlowTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final long CARGO_VENTAS = 3; // orden de V2__datos_semilla.sql: Caja, Compras, Ventas, ...
    private static final long ROL_VENTAS = 2; // orden de V2__datos_semilla.sql: ADMINISTRADOR, VENTAS, ...

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
    void listadoDeProductosTraeCategoriaYMarca() {
        String token = login();
        long categoriaId = postAndGetId(token, "/api/categorias", """
                {"nombre":"Categoria Enriquecida"}
                """);
        long marcaId = postAndGetId(token, "/api/marcas", """
                {"nombre":"Marca Enriquecida"}
                """);
        String body = """
                {
                  "codigoBarras": "7509999990001",
                  "codigo": "ENRIQ-001",
                  "nombre": "Producto enriquecido",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "categoriaId": %d,
                  "marcaId": %d,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(categoriaId, marcaId);
        postAndGetId(token, "/api/productos", body);

        var fila = buscarPorCampo(get(token, "/api/productos?size=200"), "codigo", "ENRIQ-001");
        assertThat(fila.path("categoriaNombre").asText()).isEqualTo("Categoria Enriquecida");
        assertThat(fila.path("marcaNombre").asText()).isEqualTo("Marca Enriquecida");
    }

    @Test
    void listadoDeVentasTraeNombreDeCliente() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long clienteId = postAndGetId(token, "/api/clientes", """
                {"dni":"99988811","nombre":"Cliente","apellidos":"Enriquecido","tipo":"NATURAL"}
                """);
        long productoId = crearYComprarProducto(token, almacenId, "ENRIQ-VENTA");

        String body = """
                {
                  "tipoDocumentoId": 2,
                  "clienteId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 1, "precioUnitario": 10.00}]
                }
                """.formatted(clienteId, almacenId, productoId);
        long ventaId = postAndGetId(token, "/api/ventas", body);

        var fila = buscarPorId(get(token, "/api/ventas?clienteId=" + clienteId), ventaId);
        assertThat(fila.path("clienteNombre").asText()).isEqualTo("Cliente Enriquecido");
    }

    @Test
    void listadoDeComprasTraeRazonSocialDeProveedor() {
        String token = login();
        long almacenId = crearAlmacen(token);
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"20444444440","razonSocial":"Proveedor Enriquecido","direccion":{"direccionLinea":"Calle 1"}}
                """);
        long productoId = crearProducto(token, "ENRIQ-COMPRA");

        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 10, "precioUnitario": 5.00}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        long compraId = postAndGetId(token, "/api/compras", body);

        var fila = buscarPorId(get(token, "/api/compras?proveedorId=" + proveedorId), compraId);
        assertThat(fila.path("proveedorRazonSocial").asText()).isEqualTo("Proveedor Enriquecido");
    }

    @Test
    void listadoDeTrasladosTraeNombresDeAmbosAlmacenes() {
        String token = login();
        long origenId = postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Origen Enriquecido","direccion":{"direccionLinea":"Av. 1"}}
                """);
        long destinoId = postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Destino Enriquecido","direccion":{"direccionLinea":"Av. 2"}}
                """);
        long productoId = crearYComprarProducto(token, origenId, "ENRIQ-TRASLADO");

        String body = """
                {
                  "almacenOrigenId": %d,
                  "almacenDestinoId": %d,
                  "detalles": [{"productoId": %d, "cantidad": 1}]
                }
                """.formatted(origenId, destinoId, productoId);
        long trasladoId = postAndGetId(token, "/api/traslados", body);

        var fila = buscarPorId(get(token, "/api/traslados?size=200"), trasladoId);
        assertThat(fila.path("almacenOrigenNombre").asText()).isEqualTo("Almacen Origen Enriquecido");
        assertThat(fila.path("almacenDestinoNombre").asText()).isEqualTo("Almacen Destino Enriquecido");
    }

    @Test
    void listadoDeUsuariosTraeCargoYRoles() {
        String token = login();
        String body = """
                {
                  "dni": "88877766",
                  "codigo": "ENRIQ-USR",
                  "login": "usuario-enriquecido",
                  "nombre": "Usuario",
                  "apellidos": "Enriquecido",
                  "password": "password-enriquecido-1",
                  "telefono": "999999999",
                  "sexo": "M",
                  "cargoId": %d,
                  "rolIds": [%d]
                }
                """.formatted(CARGO_VENTAS, ROL_VENTAS);
        long usuarioId = postAndGetId(token, "/api/usuarios", body);

        var fila = buscarPorId(get(token, "/api/usuarios?size=200"), usuarioId);
        assertThat(fila.path("cargoNombre").asText()).isNotBlank();
        assertThat(fila.path("roles")).isNotEmpty();
    }

    private long crearYComprarProducto(String token, long almacenId, String codigo) {
        long productoId = crearProducto(token, codigo);
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"2033333333%s","razonSocial":"Proveedor Auxiliar","direccion":{"direccionLinea":"Calle 1"}}
                """.formatted(codigo.substring(codigo.length() - 1)));
        String body = """
                {
                  "tipoDocumentoId": 1,
                  "proveedorId": %d,
                  "almacenId": %d,
                  "monedaId": 1,
                  "detalles": [{"productoId": %d, "cantidad": 20, "precioUnitario": 5.00}]
                }
                """.formatted(proveedorId, almacenId, productoId);
        postAndGetId(token, "/api/compras", body);
        return productoId;
    }

    private long crearProducto(String token, String codigo) {
        String body = """
                {
                  "codigoBarras": "750%s",
                  "codigo": "%s",
                  "nombre": "Producto auxiliar",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigo, codigo);
        return postAndGetId(token, "/api/productos", body);
    }

    private long crearAlmacen(String token) {
        return postAndGetId(token, "/api/almacenes", """
                {"nombre":"Almacen Base Enriquecido","direccion":{"direccionLinea":"Av. Base 1"}}
                """);
    }

    private JsonNode buscarPorCampo(java.util.List<JsonNode> lista, String campo, String valor) {
        return lista.stream()
                .filter(n -> n.path(campo).asText().equals(valor))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró fila con " + campo + "=" + valor));
    }

    private JsonNode buscarPorId(java.util.List<JsonNode> lista, long id) {
        return lista.stream()
                .filter(n -> n.path("id").asLong() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró fila con id=" + id));
    }

    private java.util.List<JsonNode> get(String token, String uri) {
        var node = restTestClient
                .get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        java.util.List<JsonNode> lista = new java.util.ArrayList<>();
        node.path("content").forEach(lista::add);
        return lista;
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
