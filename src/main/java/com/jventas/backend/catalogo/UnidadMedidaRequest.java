package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record UnidadMedidaRequest(@NotBlank String nombre, String abreviatura) {
}
