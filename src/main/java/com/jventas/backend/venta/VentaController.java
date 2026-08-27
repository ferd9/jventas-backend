package com.jventas.backend.venta;

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
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @GetMapping
    @PreAuthorize("hasAuthority('venta:ver')")
    public Page<VentaResumenResponse> listar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            Pageable pageable) {
        return ventaService.listar(clienteId, fechaDesde, fechaHasta, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('venta:ver')")
    public VentaDetalleResponse obtener(@PathVariable Long id) {
        return ventaService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('venta:crear')")
    public ResponseEntity<VentaDetalleResponse> crear(@Valid @RequestBody VentaRequest request, Authentication authentication) {
        VentaDetalleResponse creada = ventaService.crear(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('venta:anular')")
    public VentaDetalleResponse anular(@PathVariable Long id, Authentication authentication) {
        return ventaService.anular(id, authentication.getName());
    }
}
