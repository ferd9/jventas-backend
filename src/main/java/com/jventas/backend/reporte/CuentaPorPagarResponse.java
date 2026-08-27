package com.jventas.backend.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuentaPorPagarResponse(
        Long compraId,
        String numeroDocumento,
        Long proveedorId,
        String proveedorRazonSocial,
        LocalDate fecha,
        LocalDate fechaVencimiento,
        boolean vencido,
        BigDecimal total,
        BigDecimal pagado,
        BigDecimal saldo) {}
