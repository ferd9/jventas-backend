package com.jventas.backend.catalogo;

import java.math.BigDecimal;

public record ImpuestoResponse(Long id, String nombre, BigDecimal tasa, boolean esDefault) {

    public static ImpuestoResponse from(Impuesto impuesto) {
        return new ImpuestoResponse(impuesto.getId(), impuesto.getNombre(), impuesto.getTasa(), impuesto.isEsDefault());
    }
}
