package com.jventas.backend.venta;

import java.math.BigDecimal;

public record DetalleVentaResponse(
        Long id,
        Long productoId,
        String productoNombre,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoPct,
        BigDecimal montoImpuesto,
        BigDecimal subtotal) {

    public static DetalleVentaResponse from(DetalleVenta d) {
        return new DetalleVentaResponse(
                d.getId(),
                d.getProducto().getId(),
                d.getProducto().getNombre(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getDescuentoPct(),
                d.getMontoImpuesto(),
                d.getSubtotal());
    }
}
