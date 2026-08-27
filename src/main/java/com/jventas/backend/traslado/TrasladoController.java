package com.jventas.backend.traslado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traslados")
@RequiredArgsConstructor
public class TrasladoController {

    private final TrasladoService trasladoService;

    @GetMapping
    @PreAuthorize("hasAuthority('traslado:ver')")
    public Page<TrasladoResumenResponse> listar(Pageable pageable) {
        return trasladoService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('traslado:ver')")
    public TrasladoDetalleResponse obtener(@PathVariable Long id) {
        return trasladoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('traslado:crear')")
    public ResponseEntity<TrasladoDetalleResponse> crear(@Valid @RequestBody TrasladoRequest request, Authentication authentication) {
        TrasladoDetalleResponse creado = trasladoService.crear(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/{id}/completar")
    @PreAuthorize("hasAuthority('traslado:completar')")
    public TrasladoDetalleResponse completar(@PathVariable Long id, Authentication authentication) {
        return trasladoService.completar(id, authentication.getName());
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('traslado:anular')")
    public TrasladoDetalleResponse anular(@PathVariable Long id, Authentication authentication) {
        return trasladoService.anular(id, authentication.getName());
    }
}
