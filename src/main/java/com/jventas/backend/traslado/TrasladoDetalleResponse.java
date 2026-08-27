package com.jventas.backend.traslado;

import java.time.LocalDate;
import java.util.List;

public record TrasladoDetalleResponse(
        Long id,
        Long almacenOrigenId,
        String almacenOrigenNombre,
        Long almacenDestinoId,
        String almacenDestinoNombre,
        String usuarioNombre,
        EstadoTraslado estado,
        String observaciones,
        LocalDate fecha,
        List<DetalleTrasladoResponse> detalles) {

    public static TrasladoDetalleResponse from(TrasladoAlmacen t, List<DetalleTraslado> detalles) {
        return new TrasladoDetalleResponse(
                t.getId(),
                t.getAlmacenOrigen().getId(),
                t.getAlmacenOrigen().getNombre(),
                t.getAlmacenDestino().getId(),
                t.getAlmacenDestino().getNombre(),
                t.getUsuario().getNombre() + " " + t.getUsuario().getApellidos(),
                t.getEstado(),
                t.getObservaciones(),
                t.getFecha(),
                detalles.stream().map(DetalleTrasladoResponse::from).toList());
    }
}
