package com.jventas.backend.compra;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompraResumenResponse(
        Long id,
        String numeroDocumento,
        String proveedorRazonSocial,
        EstadoTransaccion estado,
        BigDecimal total,
        LocalDate fecha) {

    public static CompraResumenResponse from(Compra c) {
        return new CompraResumenResponse(
                c.getId(), c.getNumeroDocumento(), c.getProveedor().getRazonSocial(), c.getEstado(), c.getTotal(), c.getFecha());
    }
}
