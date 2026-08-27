package com.jventas.backend.devolucion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DevolucionRequest(String motivo, @NotEmpty List<@Valid DevolucionLineaRequest> lineas) {
}
