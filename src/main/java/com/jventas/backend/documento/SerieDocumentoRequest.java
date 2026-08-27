package com.jventas.backend.documento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SerieDocumentoRequest(@NotNull Long almacenId, @NotNull Long tipoDocumentoId, @NotBlank String serie) {
}
