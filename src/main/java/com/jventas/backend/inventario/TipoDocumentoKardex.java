package com.jventas.backend.inventario;

/** Espejo del enum nativo de Postgres `tipo_documento_kardex` — interno del sistema, no editable por el usuario. */
public enum TipoDocumentoKardex {
    APERTURA,
    COMPRA, COMPRA_ACTUALIZACION, PRODUCTO_ELIMINADO_COMPRA,
    VENTA, VENTA_ACTUALIZACION, PRODUCTO_ELIMINADO_VENTA,
    TRASLADO_SALIDA, TRASLADO_ENTRADA,
    DEVOLUCION_VENTA, DEVOLUCION_COMPRA
}
