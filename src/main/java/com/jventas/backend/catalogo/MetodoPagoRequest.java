package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record MetodoPagoRequest(@NotBlank String nombre) {
}
