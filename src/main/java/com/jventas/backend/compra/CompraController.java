package com.jventas.backend.compra;

import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @GetMapping
    @PreAuthorize("hasAuthority('compra:ver')")
    public Page<CompraResumenResponse> listar(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            Pageable pageable) {
        return compraService.listar(proveedorId, fechaDesde, fechaHasta, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('compra:ver')")
    public CompraDetalleResponse obtener(@PathVariable Long id) {
        return compraService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('compra:crear')")
    public ResponseEntity<CompraDetalleResponse> crear(@Valid @RequestBody CompraRequest request, Authentication authentication) {
        CompraDetalleResponse creada = compraService.crear(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('compra:anular')")
    public CompraDetalleResponse anular(@PathVariable Long id, Authentication authentication) {
        return compraService.anular(id, authentication.getName());
    }
}
