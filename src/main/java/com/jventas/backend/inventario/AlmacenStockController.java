package com.jventas.backend.inventario;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AlmacenStockController {

    private final AlmacenStockService almacenStockService;

    /** Al menos uno de los dos parámetros es obligatorio. */
    @GetMapping("/api/stock")
    @PreAuthorize("hasAuthority('stock:ver')")
    public List<AlmacenStockResponse> consultar(
            @RequestParam(required = false) Long almacenId, @RequestParam(required = false) Long productoId) {
        if (almacenId != null) {
            return almacenStockService.porAlmacen(almacenId);
        }
        if (productoId != null) {
            return almacenStockService.porProducto(productoId);
        }
        throw new IllegalArgumentException("Debe indicar almacenId o productoId");
    }

    @GetMapping("/api/stock/bajo-minimo")
    @PreAuthorize("hasAuthority('stock:ver')")
    public List<AlmacenStockResponse> bajoElMinimo() {
        return almacenStockService.conStockBajoElMinimo();
    }

    @PostMapping("/api/aperturas")
    @PreAuthorize("hasAuthority('almacen:apertura')")
    public List<AlmacenStockResponse> registrarApertura(@Valid @RequestBody AperturaRequest request, Authentication authentication) {
        return almacenStockService.registrarApertura(request, authentication.getName());
    }
}
