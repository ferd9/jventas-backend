package com.jventas.backend.pago;

import java.math.BigDecimal;

/** Proyección para sumar pagos por lote de documentos en una sola consulta (evita N+1 en reportes). */
public interface SaldoAgrupado {
    Long getDocumentoId();

    BigDecimal getPagado();
}
