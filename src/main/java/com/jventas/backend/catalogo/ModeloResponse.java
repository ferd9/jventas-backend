package com.jventas.backend.catalogo;

public record ModeloResponse(Long id, String nombre, Long marcaId) {

    public static ModeloResponse from(Modelo modelo) {
        return new ModeloResponse(modelo.getId(), modelo.getNombre(), modelo.getMarca().getId());
    }
}
