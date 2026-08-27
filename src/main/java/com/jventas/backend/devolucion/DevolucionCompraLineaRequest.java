package com.jventas.backend.devolucion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record DevolucionCompraLineaRequest(
        @NotNull Long detalleCompraId,
        @Positive int cantidad,
        /** Obligatorio (y del mismo tamaño que cantidad) solo si el producto de esa línea exige serie -- cuáles unidades concretas vuelven al proveedor. */
        List<String> numerosSerie) {
}
