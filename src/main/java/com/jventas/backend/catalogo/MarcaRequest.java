package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record MarcaRequest(@NotBlank String nombre) {
}
