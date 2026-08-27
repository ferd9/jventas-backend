package com.jventas.backend.traslado;

import java.time.LocalDate;

public record TrasladoResumenResponse(
        Long id, String almacenOrigenNombre, String almacenDestinoNombre, EstadoTraslado estado, LocalDate fecha) {

    public static TrasladoResumenResponse from(TrasladoAlmacen t) {
        return new TrasladoResumenResponse(
                t.getId(), t.getAlmacenOrigen().getNombre(), t.getAlmacenDestino().getNombre(), t.getEstado(), t.getFecha());
    }
}
