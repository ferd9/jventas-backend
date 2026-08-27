package com.jventas.backend.producto;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import com.jventas.backend.usuario.Cargo;
import com.jventas.backend.usuario.CargoRepository;
import com.jventas.backend.usuario.Rol;
import com.jventas.backend.usuario.RolRepository;
import com.jventas.backend.usuario.SexoPersona;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Existe porque un @PreAuthorize denegado devolvía 500 en vez de 403 hasta
 * que se agregó el @ExceptionHandler(AccessDeniedException) — un bug que
 * solo apareció probando con un usuario de permisos limitados de verdad,
 * no con el context-load test. Queda automatizado para no repetir el hallazgo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ProductoSecurityTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final String VENDEDOR_PASSWORD = "test-vendedor-password";

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
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private CargoRepository cargoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void sinTokenDevuelve401NoQuinientos() {
        restTestClient.get().uri("/api/productos").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void administradorPuedeCrearProducto() {
        String token = login("admin", ADMIN_PASSWORD);

        restTestClient
                .post()
                .uri("/api/productos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(productoJson("SEC-001"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void usuarioSinPermisoRecibe403NoQuinientos() {
        crearUsuarioDeVentasSinPermisoDeCreacion();
        String token = login("vendedor-test", VENDEDOR_PASSWORD);

        restTestClient
                .post()
                .uri("/api/productos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(productoJson("SEC-002"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void crearUsuarioDeVentasSinPermisoDeCreacion() {
        if (usuarioRepository.findByLoginAndActivoTrue("vendedor-test").isPresent()) {
            return;
        }
        Cargo cargo = cargoRepository.findByNombre("Ventas").orElseThrow();
        Rol rolVentas = rolRepository.findByNombre("VENTAS").orElseThrow();

        Usuario usuario = new Usuario();
        usuario.setDni("11111111");
        usuario.setCodigo("VTEST");
        usuario.setLogin("vendedor-test");
        usuario.setNombre("Vendedora");
        usuario.setApellidos("De Prueba");
        usuario.setPasswordHash(passwordEncoder.encode(VENDEDOR_PASSWORD));
        usuario.setTelefono("000000000");
        usuario.setSexo(SexoPersona.M);
        usuario.setCargo(cargo);
        usuario.setActivo(true);
        usuario.setRoles(Set.of(rolVentas));
        usuarioRepository.save(usuario);
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

    private String productoJson(String codigo) {
        return """
                {
                  "codigoBarras": "%s",
                  "codigo": "%s",
                  "nombre": "Producto de prueba",
                  "costo": 5,
                  "stockMinimo": 1,
                  "tipo": "INSUMO",
                  "monedaId": 1,
                  "precios": [{"listaPrecioId": 1, "precio": 10}]
                }
                """.formatted(codigo, codigo);
    }
}
