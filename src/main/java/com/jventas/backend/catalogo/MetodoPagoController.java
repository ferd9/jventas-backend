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
@RequestMapping("/api/metodos-pago")
@RequiredArgsConstructor
public class MetodoPagoController {

    private final MetodoPagoRepository metodoPagoRepository;
    private final MetodoPagoService metodoPagoService;

    @GetMapping
    public List<MetodoPagoResponse> listar() {
        return metodoPagoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(MetodoPagoResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<MetodoPagoResponse> crear(@Valid @RequestBody MetodoPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metodoPagoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public MetodoPagoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MetodoPagoRequest request) {
        return metodoPagoService.actualizar(id, request);
    }
}
