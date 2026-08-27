package com.jventas.backend.devolucion;

import java.math.BigDecimal;

public record DetalleDevolucionResponse(
        Long id, Long detalleVentaId, Long productoId, String productoNombre, int cantidad, BigDecimal monto, BigDecimal montoImpuesto) {

    public static DetalleDevolucionResponse from(DetalleDevolucion d) {
        return new DetalleDevolucionResponse(
                d.getId(),
                d.getDetalleVenta().getId(),
                d.getDetalleVenta().getProducto().getId(),
                d.getDetalleVenta().getProducto().getNombre(),
                d.getCantidad(),
                d.getMonto(),
                d.getMontoImpuesto());
    }
}
