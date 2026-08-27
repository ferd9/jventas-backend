package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record TipoDocumentoRequest(@NotBlank String nombre, boolean aplicaCompra, boolean aplicaVenta) {
}
