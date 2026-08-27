package com.jventas.backend.archivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jventas.backend.seguridad.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Cubre la subida real de imágenes: guardado en disco, validación de tipo/tamaño y que el archivo quede servible. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ArchivoFlowTests {

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
    static void configuracion(DynamicPropertyRegistry registry) {
        registry.add("jventas.bootstrap.admin.password", () -> ADMIN_PASSWORD);
        registry.add("jventas.storage.upload-dir", () -> {
            try {
                return java.nio.file.Files.createTempDirectory("jventas-archivos-test").toString();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void subirImagenValidaDevuelveUrlServible() {
        String token = login();

        // PNG de 1x1 mínimo válido -- suficiente para pasar la validación de tipo de contenido
        byte[] pngMinimo = pngDeUnPixel();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("archivo", new ByteArrayResource(pngMinimo) {
                    @Override
                    public String getFilename() {
                        return "producto.png";
                    }
                })
                .contentType(MediaType.IMAGE_PNG);

        var respuesta = restTestClient
                .post()
                .uri("/api/archivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ArchivoResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.url()).startsWith("/api/archivos/").endsWith(".png");

        // el GET (público, sin token) debe servir el archivo recién subido
        restTestClient
                .get()
                .uri(respuesta.url())
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void subirSinAutenticacionEsRechazado() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("archivo", new ByteArrayResource(pngDeUnPixel()) {
                    @Override
                    public String getFilename() {
                        return "producto.png";
                    }
                })
                .contentType(MediaType.IMAGE_PNG);

        restTestClient
                .post()
                .uri("/api/archivos")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void tipoDeArchivoNoPermitidoEsRechazado() {
        String token = login();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("archivo", new ByteArrayResource("no soy una imagen".getBytes())
                        {
                            @Override
                            public String getFilename() {
                                return "script.exe";
                            }
                        })
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        restTestClient
                .post()
                .uri("/api/archivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .exchange()
                .expectStatus()
                .isBadRequest();
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

    private byte[] pngDeUnPixel() {
        // PNG válido de 1x1 transparente, codificado en base64 -- el más pequeño posible
        String base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        return java.util.Base64.getDecoder().decode(base64);
    }
}
