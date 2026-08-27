package com.jventas.backend.catalogo;

public record UnidadMedidaResponse(Long id, String nombre, String abreviatura) {

    public static UnidadMedidaResponse from(UnidadMedida unidad) {
        return new UnidadMedidaResponse(unidad.getId(), unidad.getNombre(), unidad.getAbreviatura());
    }
}
