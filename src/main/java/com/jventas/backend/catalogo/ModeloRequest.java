package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModeloRequest(@NotBlank String nombre, @NotNull Long marcaId) {
}
