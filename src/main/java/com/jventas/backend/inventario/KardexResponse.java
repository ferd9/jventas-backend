package com.jventas.backend.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KardexResponse(
        Long id,
        LocalDate fecha,
        TipoDocumentoKardex tipoDocumento,
        String numeroDocumento,
        int entrada,
        int salida,
        BigDecimal precio,
        BigDecimal valor,
        BigDecimal costoUnitario,
        BigDecimal costoTotal,
        int stockResultante,
        BigDecimal valorTotal) {

    public static KardexResponse from(Kardex k) {
        return new KardexResponse(
                k.getId(),
                k.getFecha(),
                k.getTipoDocumento(),
                k.getNumeroDocumento(),
                k.getEntrada(),
                k.getSalida(),
                k.getPrecio(),
                k.getValor(),
                k.getCostoUnitario(),
                k.getCostoTotal(),
                k.getStockResultante(),
                k.getValorTotal());
    }
}
