package com.jventas.backend.producto;

/** Forma liviana para listados — sin precios ni todas las relaciones expandidas. */
public record ProductoResumenResponse(
        Long id, String codigo, String codigoBarras, String nombre, String categoriaNombre, String marcaNombre, boolean activo) {

    public static ProductoResumenResponse from(Producto producto) {
        return new ProductoResumenResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getCodigoBarras(),
                producto.getNombre(),
                producto.getCategoria() != null ? producto.getCategoria().getNombre() : null,
                producto.getMarca() != null ? producto.getMarca().getNombre() : null,
                producto.isActivo());
    }
}
