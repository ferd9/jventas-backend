package com.jventas.backend.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record DetalleVentaRequest(
        @NotNull Long productoId,
        @Positive int cantidad,
        @NotNull @DecimalMin("0.0") BigDecimal precioUnitario,
        BigDecimal descuentoPct,
        Long impuestoId,
        /** Obligatorio (y del mismo tamaño que cantidad) solo si la categoría del producto exige serie -- el vendedor elige manualmente cuáles salen. */
        List<String> numerosSerie) {
}
