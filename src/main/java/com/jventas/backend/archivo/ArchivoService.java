package com.jventas.backend.archivo;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda en disco local (filesystem), no en la base de datos -- suficiente
 * para un solo servidor. Si esto corre alguna vez detrás de varias
 * instancias o necesita sobrevivir a un redeploy sin volumen persistente,
 * hay que moverlo a almacenamiento de objetos (S3 o similar).
 */
@Service
@RequiredArgsConstructor
public class ArchivoService {

    private static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024; // 5 MB

    private static final Map<String, String> EXTENSIONES_PERMITIDAS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

    private final ArchivoStorageProperties storageProperties;

    @PostConstruct
    void crearDirectorioDeSubida() {
        try {
            Files.createDirectories(directorioBase());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de subida: " + storageProperties.uploadDir(), e);
        }
    }

    public String guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido (5 MB)");
        }
        String extension = EXTENSIONES_PERMITIDAS.get(archivo.getContentType());
        if (extension == null) {
            throw new IllegalArgumentException("Tipo de archivo no permitido: " + archivo.getContentType() + " (solo imágenes JPEG, PNG, WEBP o GIF)");
        }

        // nombre generado, nunca el original -- evita path traversal y colisiones
        String nombreArchivo = UUID.randomUUID() + "." + extension;
        Path destino = directorioBase().resolve(nombreArchivo);

        try {
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }

        return "/api/archivos/" + nombreArchivo;
    }

    private Path directorioBase() {
        return Path.of(storageProperties.uploadDir()).toAbsolutePath().normalize();
    }
}
