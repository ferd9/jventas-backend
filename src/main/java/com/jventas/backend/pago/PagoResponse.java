package com.jventas.backend.pago;

import java.math.BigDecimal;
import java.time.Instant;

public record PagoResponse(
        Long id, Long compraId, Long ventaId, String metodoPagoNombre, BigDecimal monto, Instant fechaPago, String referencia) {

    public static PagoResponse from(Pago p) {
        return new PagoResponse(
                p.getId(),
                p.getCompra() != null ? p.getCompra().getId() : null,
                p.getVenta() != null ? p.getVenta().getId() : null,
                p.getMetodoPago().getNombre(),
                p.getMonto(),
                p.getFechaPago(),
                p.getReferencia());
    }
}
