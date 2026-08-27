package com.jventas.backend.venta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record VentaRequest(
        @NotNull Long tipoDocumentoId,
        String numeroDocumento,
        Long serieDocumentoId, // si viene, el número correlativo se genera solo y pisa numeroDocumento
        @NotNull Long clienteId,
        @NotNull Long almacenId,
        @NotNull Long monedaId,
        LocalDate fechaVencimiento,
        String observaciones,
        @NotEmpty List<@Valid DetalleVentaRequest> detalles) {
}
