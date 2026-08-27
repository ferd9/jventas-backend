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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impuestos")
@RequiredArgsConstructor
public class ImpuestoController {

    private final ImpuestoRepository impuestoRepository;
    private final ImpuestoService impuestoService;

    @GetMapping
    public List<ImpuestoResponse> listar() {
        return impuestoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(ImpuestoResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<ImpuestoResponse> crear(@Valid @RequestBody ImpuestoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(impuestoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ImpuestoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ImpuestoRequest request) {
        return impuestoService.actualizar(id, request);
    }
}
