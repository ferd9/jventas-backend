package com.jventas.backend.traslado;

public record DetalleTrasladoResponse(Long id, Long productoId, String productoNombre, int cantidad) {

    public static DetalleTrasladoResponse from(DetalleTraslado d) {
        return new DetalleTrasladoResponse(d.getId(), d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad());
    }
}
