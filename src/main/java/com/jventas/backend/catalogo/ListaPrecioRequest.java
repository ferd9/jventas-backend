package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record ListaPrecioRequest(@NotBlank String nombre) {
}
