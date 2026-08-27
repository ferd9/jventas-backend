package com.jventas.backend.pago;

import java.math.BigDecimal;

public record SaldoResponse(BigDecimal total, BigDecimal pagado, BigDecimal saldo) {
}
