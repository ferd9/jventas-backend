package com.jventas.backend.almacen;

import jakarta.validation.constraints.NotNull;

public record EncargadoAlmacenRequest(@NotNull Long usuarioId, @NotNull Long almacenId, @NotNull TipoCargoAlmacen tipoCargo) {
}
