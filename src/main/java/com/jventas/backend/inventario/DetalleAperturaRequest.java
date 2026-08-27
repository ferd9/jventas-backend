package com.jventas.backend.inventario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DetalleAperturaRequest(@NotNull Long productoId, @PositiveOrZero int cantidad) {
}
