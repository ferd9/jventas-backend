package com.jventas.backend.compra;

import java.math.BigDecimal;

public record DetalleCompraResponse(
        Long id,
        Long productoId,
        String productoNombre,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoPct,
        BigDecimal montoImpuesto,
        BigDecimal subtotal) {

    public static DetalleCompraResponse from(DetalleCompra d) {
        return new DetalleCompraResponse(
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
