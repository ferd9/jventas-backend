package com.jventas.backend.archivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Solo sube el archivo y devuelve su URL -- asociarla a un producto (o
 * cualquier otra entidad) es responsabilidad del cliente, vía el campo
 * imagenUrl normal en el PUT/POST correspondiente. Servir el archivo lo hace
 * el resource handler de WebConfig, no este controller.
 */
@RestController
@RequestMapping("/api/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService archivoService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ArchivoResponse> subir(@RequestParam("archivo") MultipartFile archivo) {
        String url = archivoService.guardar(archivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ArchivoResponse(url));
    }
}
