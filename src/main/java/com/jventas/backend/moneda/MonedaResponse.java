package com.jventas.backend.moneda;

/** Forma que ve el cliente HTTP — separada de la entidad JPA a propósito. */
public record MonedaResponse(
        Long id,
        String nombre,
        String simbolo,
        String codigoIso,
        boolean predeterminada) {

    public static MonedaResponse from(Moneda moneda) {
        return new MonedaResponse(
                moneda.getId(),
                moneda.getNombre(),
                moneda.getSimbolo(),
                moneda.getCodigoIso(),
                moneda.isPredeterminada());
    }
}
