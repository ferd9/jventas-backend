package com.jventas.backend.devolucion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DevolucionCompraResponse(
        Long id,
        Long compraId,
        String usuarioLogin,
        Instant fecha,
        String motivo,
        BigDecimal montoTotal,
        List<DetalleDevolucionCompraResponse> lineas) {

    public static DevolucionCompraResponse from(DevolucionCompra d, List<DetalleDevolucionCompra> lineas) {
        return new DevolucionCompraResponse(
                d.getId(),
                d.getCompra().getId(),
                d.getUsuario().getLogin(),
                d.getFecha(),
                d.getMotivo(),
                d.getMontoTotal(),
                lineas.stream().map(DetalleDevolucionCompraResponse::from).toList());
    }
}
