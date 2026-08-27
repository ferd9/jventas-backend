package com.jventas.backend.serieproducto;

public record SerieProductoResponse(
        Long id, String numeroSerie, Long productoId, String productoNombre, Long almacenId, String almacenNombre, boolean vendido) {

    public static SerieProductoResponse from(SerieProducto s) {
        return new SerieProductoResponse(
                s.getId(),
                s.getNumeroSerie(),
                s.getProducto().getId(),
                s.getProducto().getNombre(),
                s.getAlmacen() != null ? s.getAlmacen().getId() : null,
                s.getAlmacen() != null ? s.getAlmacen().getNombre() : null,
                s.isVendido());
    }
}
