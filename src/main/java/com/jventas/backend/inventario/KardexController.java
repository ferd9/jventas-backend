package com.jventas.backend.inventario;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kardex")
@RequiredArgsConstructor
public class KardexController {

    private final KardexRepository kardexRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('kardex:ver')")
    public Page<KardexResponse> listar(@RequestParam Long almacenId, @RequestParam Long productoId, Pageable pageable) {
        return kardexRepository
                .findByAlmacenIdAndProductoIdOrderByFechaAscIdAsc(almacenId, productoId, pageable)
                .map(KardexResponse::from);
    }
}
