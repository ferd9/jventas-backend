package com.jventas.backend.almacen;

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
 * Un usuario sin asignaciones en encargado_almacen (como el admin del
 * bootstrap) no tiene restricción — cubre administradores. Un usuario CON
 * asignaciones queda limitado exactamente a esos almacenes, aunque tenga
 * el permiso general venta:crear.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AccesoAlmacenFlowTests {

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
    void vendedorAsignadoSoloOperaSuAlmacen() {
        String adminToken = login("admin", ADMIN_PASSWORD);

        long almacenPermitido = crearAlmacen(adminToken, "Almacen Permitido");
        long almacenAjeno = crearAlmacen(adminToken, "Almacen Ajeno");
        long clienteId = crearCliente(adminToken);
        long productoId = crearProducto(adminToken);

        long vendedorId = crearUsuarioVendedor(adminToken);
        asignarEncargado(adminToken, vendedorId, almacenPermitido);

        String vendedorToken = login("vendedor-scoped", "password-vendedor-1");

        // sin token de vendedor, este producto no tiene stock en ningún lado --
        // primero hay que comprar en el almacén permitido para poder vender ahí.
        registrarCompra(adminToken, almacenPermitido, productoId);

        // el vendedor SÍ puede vender desde su almacén asignado
        restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + vendedorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ventaJson(clienteId, almacenPermitido, productoId))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED);

        // el vendedor NO puede vender desde un almacén que no le asignaron,
        // aunque tenga el permiso general venta:crear
        restTestClient
                .post()
                .uri("/api/ventas")
                .header("Authorization", "Bearer " + vendedorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ventaJson(clienteId, almacenAjeno, productoId))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String login(String login, String password) {
        String body = """
                {"login":"%s","password":"%s"}
                """.formatted(login, password);
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

    private long crearCliente(String token) {
        return postAndGetId(token, "/api/clientes", """
                {"dni":"99988877","nombre":"Cliente","apellidos":"Acceso","tipo":"NATURAL"}
                """);
    }

    private long crearProducto(String token) {
        String body = """
                {
                  "codigoBarras": "7501234509999",
                  "codigo": "ACCESO-001",
                  "nombre": "Producto de acceso",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """;
        return postAndGetId(token, "/api/productos", body);
    }

    private long crearUsuarioVendedor(String adminToken) {
        String body = """
                {
                  "dni": "11122233",
                  "codigo": "VEND-SCOPED",
                  "login": "vendedor-scoped",
                  "nombre": "Vendedora",
                  "apellidos": "Con Alcance",
                  "password": "password-vendedor-1",
                  "telefono": "999999999",
                  "sexo": "M",
                  "cargoId": %d,
                  "rolIds": [%d]
                }
                """.formatted(CARGO_VENTAS, ROL_VENTAS);
        return postAndGetId(adminToken, "/api/usuarios", body);
    }

    private void asignarEncargado(String adminToken, long usuarioId, long almacenId) {
        String body = """
                {"usuarioId": %d, "almacenId": %d, "tipoCargo": "EMPLEADO"}
                """.formatted(usuarioId, almacenId);
        postAndGetId(adminToken, "/api/encargados-almacen", body);
    }

    private void registrarCompra(String token, long almacenId, long productoId) {
        long proveedorId = postAndGetId(token, "/api/proveedores", """
                {"ruc":"20444444444","razonSocial":"Proveedor Acceso","direccion":{"direccionLinea":"Calle 1"}}
                """);
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
