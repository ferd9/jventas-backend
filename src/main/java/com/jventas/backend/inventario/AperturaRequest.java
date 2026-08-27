package com.jventas.backend.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AperturaRequest(@NotNull Long almacenId, @NotEmpty List<@Valid DetalleAperturaRequest> detalles) {
}
