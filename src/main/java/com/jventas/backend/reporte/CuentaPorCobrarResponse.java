package com.jventas.backend.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuentaPorCobrarResponse(
        Long ventaId,
        String numeroDocumento,
        Long clienteId,
        String clienteNombre,
        LocalDate fecha,
        LocalDate fechaVencimiento,
        boolean vencido,
        BigDecimal total,
        BigDecimal pagado,
        BigDecimal saldo) {}
