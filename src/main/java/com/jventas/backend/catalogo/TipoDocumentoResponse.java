package com.jventas.backend.catalogo;

public record TipoDocumentoResponse(Long id, String nombre, boolean aplicaCompra, boolean aplicaVenta) {

    public static TipoDocumentoResponse from(TipoDocumento tipoDocumento) {
        return new TipoDocumentoResponse(
                tipoDocumento.getId(), tipoDocumento.getNombre(), tipoDocumento.isAplicaCompra(), tipoDocumento.isAplicaVenta());
    }
}
