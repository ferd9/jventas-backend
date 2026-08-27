package com.jventas.backend.devolucion;

import java.math.BigDecimal;

public record DetalleDevolucionCompraResponse(
        Long id, Long detalleCompraId, Long productoId, String productoNombre, int cantidad, BigDecimal monto, BigDecimal montoImpuesto) {

    public static DetalleDevolucionCompraResponse from(DetalleDevolucionCompra d) {
        return new DetalleDevolucionCompraResponse(
                d.getId(),
                d.getDetalleCompra().getId(),
                d.getDetalleCompra().getProducto().getId(),
                d.getDetalleCompra().getProducto().getNombre(),
                d.getCantidad(),
                d.getMonto(),
                d.getMontoImpuesto());
    }
}
