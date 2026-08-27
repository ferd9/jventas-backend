package com.jventas.backend.catalogo;

public record MarcaResponse(Long id, String nombre) {

    public static MarcaResponse from(Marca marca) {
        return new MarcaResponse(marca.getId(), marca.getNombre());
    }
}
