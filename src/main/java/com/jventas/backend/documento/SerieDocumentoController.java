package com.jventas.backend.documento;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/series-documento")
@RequiredArgsConstructor
public class SerieDocumentoController {

    private final SerieDocumentoService serieDocumentoService;

    @GetMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public List<SerieDocumentoResponse> listar(@RequestParam Long almacenId, @RequestParam Long tipoDocumentoId) {
        return serieDocumentoService.listar(almacenId, tipoDocumentoId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<SerieDocumentoResponse> crear(@Valid @RequestBody SerieDocumentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serieDocumentoService.crear(request));
    }
}
