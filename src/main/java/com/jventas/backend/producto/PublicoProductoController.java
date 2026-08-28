package com.jventas.backend.producto;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público de solo lectura -- sin autenticación (ver SecurityConfig,
 * solo GET bajo /api/publico/** está permitido sin login). Pensado para una
 * vitrina de productos que cualquier visitante puede ver, no para el panel
 * administrativo (eso sigue siendo ProductoController, con permisos).
 */
@RestController
@RequestMapping("/api/publico/productos")
@RequiredArgsConstructor
public class PublicoProductoController {

    private final ProductoService productoService;

    @GetMapping
    public Page<ProductoPublicoResponse> listar(@RequestParam(required = false) String q, Pageable pageable) {
        return productoService.listarPublico(q, pageable);
    }
}
