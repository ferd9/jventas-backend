package com.jventas.backend.devolucion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DevolucionResponse(
        Long id,
        Long ventaId,
        String usuarioLogin,
        Instant fecha,
        String motivo,
        BigDecimal montoTotal,
        List<DetalleDevolucionResponse> lineas) {

    public static DevolucionResponse from(Devolucion d, List<DetalleDevolucion> lineas) {
        return new DevolucionResponse(
                d.getId(),
                d.getVenta().getId(),
                d.getUsuario().getLogin(),
                d.getFecha(),
                d.getMotivo(),
                d.getMontoTotal(),
                lineas.stream().map(DetalleDevolucionResponse::from).toList());
    }
}
