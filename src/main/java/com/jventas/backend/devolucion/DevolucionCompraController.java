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

/** Documento propio, no un PUT sobre la compra -- ver decisión de negocio en DevolucionCompraService. */
@RestController
@RequestMapping("/api/compras/{compraId}/devoluciones")
@RequiredArgsConstructor
public class DevolucionCompraController {

    private final DevolucionCompraService devolucionCompraService;

    @PostMapping
    @PreAuthorize("hasAuthority('compra:anular')")
    public ResponseEntity<DevolucionCompraResponse> registrar(
            @PathVariable Long compraId, @Valid @RequestBody DevolucionCompraRequest request, Authentication authentication) {
        DevolucionCompraResponse creada = devolucionCompraService.registrar(compraId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('compra:ver')")
    public List<DevolucionCompraResponse> listar(@PathVariable Long compraId) {
        return devolucionCompraService.listar(compraId);
    }
}
