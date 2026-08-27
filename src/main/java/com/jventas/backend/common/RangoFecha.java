package com.jventas.backend.common;

import java.time.LocalDate;

/**
 * Reemplaza fechaDesde/fechaHasta null por un rango centinela bien amplio
 * antes de llamar al repositorio -- comparar una columna de fecha contra un
 * parámetro JPQL genuinamente null es justo el patrón que reventó dos veces
 * (ver VentaRepository.buscar()). Los límites están muy por debajo/encima
 * de cualquier fecha de negocio real, pero dentro del rango del tipo `date`
 * de Postgres (a diferencia de LocalDate.MIN/MAX, que lo desbordarían).
 */
public final class RangoFecha {

    private static final LocalDate DESDE_POR_DEFECTO = LocalDate.of(1900, 1, 1);
    private static final LocalDate HASTA_POR_DEFECTO = LocalDate.of(2999, 12, 31);

    private RangoFecha() {}

    public static LocalDate desde(LocalDate fechaDesde) {
        return fechaDesde != null ? fechaDesde : DESDE_POR_DEFECTO;
    }

    public static LocalDate hasta(LocalDate fechaHasta) {
        return fechaHasta != null ? fechaHasta : HASTA_POR_DEFECTO;
    }
}
