package com.jventas.backend.archivo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jventas.storage")
public record ArchivoStorageProperties(String uploadDir) {

    public ArchivoStorageProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "./uploads";
        }
    }
}
