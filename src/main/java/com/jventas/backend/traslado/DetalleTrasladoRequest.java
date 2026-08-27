package com.jventas.backend.traslado;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DetalleTrasladoRequest(@NotNull Long productoId, @Positive int cantidad) {
}
