package com.jventas.backend.producto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record ProductoRequest(
        @NotBlank String codigoBarras,
        @NotBlank String codigo,
        String codigoFabricante,
        @NotBlank String nombre,
        @NotNull @DecimalMin("0.0") BigDecimal costo,
        @NotNull @PositiveOrZero Integer stockMinimo,
        @NotNull TipoProducto tipo,
        @NotNull Long monedaId,
        Long impuestoId,
        String imagenUrl,
        Long categoriaId,
        Long marcaId,
        Long modeloId,
        Long unidadMedidaId,
        String ubicacion,
        BigDecimal peso,
        @NotEmpty List<@Valid PrecioRequest> precios) {
}
