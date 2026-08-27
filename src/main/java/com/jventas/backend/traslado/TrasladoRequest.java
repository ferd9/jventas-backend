package com.jventas.backend.traslado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TrasladoRequest(
        @NotNull Long almacenOrigenId,
        @NotNull Long almacenDestinoId,
        String observaciones,
        @NotEmpty List<@Valid DetalleTrasladoRequest> detalles) {
}
