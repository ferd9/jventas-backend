package com.jventas.backend.pago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PagoRequest(Long compraId, Long ventaId, @NotNull Long metodoPagoId, @NotNull @DecimalMin("0.01") BigDecimal monto, String referencia) {

    public boolean esOrigenValido() {
        return (compraId == null) != (ventaId == null); // exactamente uno de los dos
    }
}
