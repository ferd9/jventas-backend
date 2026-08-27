package com.jventas.backend.serieproducto;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sirve para que el vendedor elija manualmente cuál serie sale al vender -- ver decisión de negocio en SerieProductoService. */
@RestController
@RequestMapping("/api/series-producto")
@RequiredArgsConstructor
public class SerieProductoController {

    private final SerieProductoRepository serieProductoRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('producto:ver')")
    public List<SerieProductoResponse> listarDisponibles(@RequestParam Long productoId, @RequestParam Long almacenId) {
        return serieProductoRepository
                .findByProductoIdAndAlmacenIdAndActivoTrueAndVendidoFalseOrderByNumeroSerieAsc(productoId, almacenId)
                .stream()
                .map(SerieProductoResponse::from)
                .toList();
    }
}
