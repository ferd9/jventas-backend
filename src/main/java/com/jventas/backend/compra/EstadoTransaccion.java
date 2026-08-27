package com.jventas.backend.compra;

/** Espejo del enum nativo de Postgres `estado_transaccion`. CANCELADO = pagado/liquidado, no anulado. */
public enum EstadoTransaccion {
    PENDIENTE, CANCELADO, ANULADO
}
