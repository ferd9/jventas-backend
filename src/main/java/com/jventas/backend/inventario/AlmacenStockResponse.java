package com.jventas.backend.inventario;

import java.time.Instant;

public record AlmacenStockResponse(
        Long almacenId, String almacenNombre, Long productoId, String productoNombre, int cantidadActual, Instant actualizadoEn) {

    public static AlmacenStockResponse from(AlmacenStock s) {
        return new AlmacenStockResponse(
                s.getAlmacen().getId(),
                s.getAlmacen().getNombre(),
                s.getProducto().getId(),
                s.getProducto().getNombre(),
                s.getCantidadActual(),
                s.getUpdatedAt());
    }
}
