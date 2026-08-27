package com.jventas.backend.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ImpuestoRequest(@NotBlank String nombre, @NotNull @DecimalMin("0.0") BigDecimal tasa, boolean esDefault) {
}
