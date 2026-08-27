package com.jventas.backend.catalogo;

public record MetodoPagoResponse(Long id, String nombre) {

    public static MetodoPagoResponse from(MetodoPago metodoPago) {
        return new MetodoPagoResponse(metodoPago.getId(), metodoPago.getNombre());
    }
}
