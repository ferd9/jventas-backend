package com.jventas.backend.catalogo;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tipos-documento")
@RequiredArgsConstructor
public class TipoDocumentoController {

    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final TipoDocumentoService tipoDocumentoService;

    @GetMapping
    public List<TipoDocumentoResponse> listar(@RequestParam(required = false) String aplicaA) {
        List<TipoDocumento> tipos = switch (aplicaA == null ? "" : aplicaA) {
            case "compra" -> tipoDocumentoRepository.findByActivoTrueAndAplicaCompraTrueOrderByNombreAsc();
            case "venta" -> tipoDocumentoRepository.findByActivoTrueAndAplicaVentaTrueOrderByNombreAsc();
            default -> tipoDocumentoRepository.findAll();
        };
        return tipos.stream().map(TipoDocumentoResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<TipoDocumentoResponse> crear(@Valid @RequestBody TipoDocumentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipoDocumentoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public TipoDocumentoResponse actualizar(@PathVariable Long id, @Valid @RequestBody TipoDocumentoRequest request) {
        return tipoDocumentoService.actualizar(id, request);
    }
}
