package com.jventas.backend.devolucion;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Documento propio, no un PUT sobre la venta -- ver decisión de negocio en DevolucionService. */
@RestController
@RequestMapping("/api/ventas/{ventaId}/devoluciones")
@RequiredArgsConstructor
public class DevolucionController {

    private final DevolucionService devolucionService;

    @PostMapping
    @PreAuthorize("hasAuthority('venta:anular')")
    public ResponseEntity<DevolucionResponse> registrar(
            @PathVariable Long ventaId, @Valid @RequestBody DevolucionRequest request, Authentication authentication) {
        DevolucionResponse creada = devolucionService.registrar(ventaId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('venta:ver')")
    public List<DevolucionResponse> listar(@PathVariable Long ventaId) {
        return devolucionService.listar(ventaId);
    }
}
