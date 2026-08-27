package com.jventas.backend.catalogo;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(@NotBlank String nombre, Long categoriaPadreId, Boolean requiereSerie) {

    /** Boolean (no boolean) a propósito -- un cliente existente que no manda el campo no debe reventar la request. */
    public CategoriaRequest {
        if (requiereSerie == null) {
            requiereSerie = false;
        }
    }
}
