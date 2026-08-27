package com.jventas.backend.archivo;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve los archivos subidos como recursos estáticos -- /api/archivos/{nombre} lee directo del disco. */
@Configuration
@EnableConfigurationProperties(ArchivoStorageProperties.class)
@RequiredArgsConstructor
public class ArchivoWebConfig implements WebMvcConfigurer {

    private final ArchivoStorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = Path.of(storageProperties.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/api/archivos/**").addResourceLocations(ubicacion);
    }
}
