package com.jventas.backend.usuario;

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
 * Cubre la administración de RBAC: listar roles/cargos/permisos sembrados,
 * crear un rol nuevo con un subconjunto de permisos, y que ese rol -- ni
 * bien asignado a un usuario -- realmente otorgue (y solo otorgue) esos
 * permisos. Sin esta última parte, probar solo el CRUD de Rol no demuestra
 * que la gestión de RBAC hace algo real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class RolFlowTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final long CARGO_VENTAS = 3; // orden de V2__datos_semilla.sql: Caja, Compras, Ventas, ...

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
    void listaLosRolesSembradosConSusPermisos() {
        // >= 5, no == 5 -- otros métodos de esta clase comparten el mismo Postgres y pueden crear roles propios
        String token = login();

        var roles = get(token, "/api/roles");
        assertThat(roles.size()).isGreaterThanOrEqualTo(5);
        var administrador = buscarPorNombre(roles, "ADMINISTRADOR");
        assertThat(administrador.path("permisos")).isNotEmpty();
    }

    @Test
    void listaCargosYPermisosSembrados() {
        String token = login();

        assertThat(get(token, "/api/cargos")).isNotEmpty();
        assertThat(get(token, "/api/permisos")).isNotEmpty();
    }

    @Test
    void rolNuevoConcedeExactamenteSusPermisosAsignados() {
        String token = login();

        long idProductoVer = idDePermiso(token, "producto:ver");
        long idStockVer = idDePermiso(token, "stock:ver");

        long rolId = crearRol(token, "SOLO-LECTURA-TEST", java.util.Set.of(idProductoVer, idStockVer));

        var rolCreado = getObjeto(token, "/api/roles/" + rolId);
        assertThat(rolCreado.path("permisos")).hasSize(2);

        long usuarioId = crearUsuarioConRol(token, rolId);
        String tokenNuevo = login("lector-test", "password-lector-1");

        // tiene el permiso que sí se asignó
        restTestClient
                .get()
                .uri("/api/productos")
                .header("Authorization", "Bearer " + tokenNuevo)
                .exchange()
                .expectStatus()
                .isOk();

        // NO tiene un permiso que nunca se asignó a este rol -- cuerpo válido a propósito,
        // para que lo único que pueda rechazar la request sea la autorización, no un 400
        restTestClient
                .post()
                .uri("/api/productos")
                .header("Authorization", "Bearer " + tokenNuevo)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "codigoBarras": "7509999998888",
                          "codigo": "ROL-TEST-001",
                          "nombre": "Producto de prueba de rol",
                          "costo": 5,
                          "stockMinimo": 1,
                          "tipo": "INSUMO",
                          "monedaId": 1,
                          "precios": [{"listaPrecioId": 1, "precio": 10}]
                        }
                        """)
                .exchange()
                .expectStatus()
                .isForbidden();

        // reasignar permisos por PUT reemplaza el set completo, no lo suma
        actualizarRol(token, rolId, "SOLO-LECTURA-TEST", java.util.Set.of(idProductoVer));
        var rolActualizado = getObjeto(token, "/api/roles/" + rolId);
        assertThat(rolActualizado.path("permisos")).hasSize(1);

        assertThat(usuarioId).isPositive();
    }

    private long idDePermiso(String token, String codigo) {
        var permisos = get(token, "/api/permisos");
        for (JsonNode permiso : permisos) {
            if (permiso.path("codigo").asText().equals(codigo)) {
                return permiso.path("id").asLong();
            }
        }
        throw new AssertionError("Permiso no encontrado en la semilla: " + codigo);
    }

    private long crearRol(String token, String nombre, java.util.Set<Long> permisoIds) {
        String idsJson = permisoIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String body = """
                {"nombre":"%s","descripcion":"Rol de prueba","permisoIds":[%s]}
                """.formatted(nombre, idsJson);
        var node = restTestClient
                .post()
                .uri("/api/roles")
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

    private void actualizarRol(String token, long rolId, String nombre, java.util.Set<Long> permisoIds) {
        String idsJson = permisoIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String body = """
                {"nombre":"%s","descripcion":"Rol de prueba","permisoIds":[%s]}
                """.formatted(nombre, idsJson);
        restTestClient
                .put()
                .uri("/api/roles/" + rolId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isOk();
    }

    private long crearUsuarioConRol(String adminToken, long rolId) {
        String body = """
                {
                  "dni": "77788899",
                  "codigo": "LECTOR-TEST",
                  "login": "lector-test",
                  "nombre": "Lector",
                  "apellidos": "De Prueba",
                  "password": "password-lector-1",
                  "telefono": "999999999",
                  "sexo": "M",
                  "cargoId": %d,
                  "rolIds": [%d]
                }
                """.formatted(CARGO_VENTAS, rolId);
        var node = restTestClient
                .post()
                .uri("/api/usuarios")
                .header("Authorization", "Bearer " + adminToken)
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

    private JsonNode buscarPorNombre(java.util.List<JsonNode> roles, String nombre) {
        return roles.stream()
                .filter(r -> r.path("nombre").asText().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Rol no encontrado: " + nombre));
    }

    private JsonNode getObjeto(String token, String uri) {
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
        assertThat(node).isNotNull();
        return node;
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
        node.forEach(lista::add);
        return lista;
    }

    private String login() {
        return login("admin", ADMIN_PASSWORD);
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
}
