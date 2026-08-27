package com.jventas.backend.documento;

public record SerieDocumentoResponse(
        Long id, Long almacenId, String almacenNombre, Long tipoDocumentoId, String tipoDocumentoNombre, String serie, int correlativoActual, String proximoNumero) {

    public static SerieDocumentoResponse from(SerieDocumento s) {
        return new SerieDocumentoResponse(
                s.getId(),
                s.getAlmacen().getId(),
                s.getAlmacen().getNombre(),
                s.getTipoDocumento().getId(),
                s.getTipoDocumento().getNombre(),
                s.getSerie(),
                s.getCorrelativoActual(),
                s.formatear(s.getCorrelativoActual() + 1));
    }
}
