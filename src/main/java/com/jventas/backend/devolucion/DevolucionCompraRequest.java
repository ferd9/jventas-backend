package com.jventas.backend.devolucion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DevolucionCompraRequest(String motivo, @NotEmpty List<@Valid DevolucionCompraLineaRequest> lineas) {
}
