package com.jventas.backend.producto;

import java.math.BigDecimal;
import java.util.List;

public record ProductoDetalleResponse(
        Long id,
        String codigoBarras,
        String codigo,
        String codigoFabricante,
        String nombre,
        BigDecimal costo,
        int stockMinimo,
        TipoProducto tipo,
        Long monedaId,
        String monedaNombre,
        Long impuestoId,
        String impuestoNombre,
        String imagenUrl,
        Long categoriaId,
        String categoriaNombre,
        Long marcaId,
        String marcaNombre,
        Long modeloId,
        String modeloNombre,
        Long unidadMedidaId,
        String unidadMedidaNombre,
        String ubicacion,
        BigDecimal peso,
        boolean activo,
        List<PrecioResponse> precios) {

    public static ProductoDetalleResponse from(Producto p, List<ProductoPrecio> precios) {
        return new ProductoDetalleResponse(
                p.getId(),
                p.getCodigoBarras(),
                p.getCodigo(),
                p.getCodigoFabricante(),
                p.getNombre(),
                p.getCosto(),
                p.getStockMinimo(),
                p.getTipo(),
                p.getMoneda().getId(),
                p.getMoneda().getNombre(),
                p.getImpuesto() != null ? p.getImpuesto().getId() : null,
                p.getImpuesto() != null ? p.getImpuesto().getNombre() : null,
                p.getImagenUrl(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNombre() : null,
                p.getMarca() != null ? p.getMarca().getId() : null,
                p.getMarca() != null ? p.getMarca().getNombre() : null,
                p.getModelo() != null ? p.getModelo().getId() : null,
                p.getModelo() != null ? p.getModelo().getNombre() : null,
                p.getUnidadMedida() != null ? p.getUnidadMedida().getId() : null,
                p.getUnidadMedida() != null ? p.getUnidadMedida().getNombre() : null,
                p.getUbicacion(),
                p.getPeso(),
                p.isActivo(),
                precios.stream().map(PrecioResponse::from).toList());
    }
}
