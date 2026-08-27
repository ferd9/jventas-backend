package com.jventas.backend.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PrecioRequest(@NotNull Long listaPrecioId, @NotNull @DecimalMin("0.0") BigDecimal precio) {
}
