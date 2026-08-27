package com.jventas.backend.compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompraDetalleResponse(
        Long id,
        String tipoDocumentoNombre,
        String numeroDocumento,
        Long proveedorId,
        String proveedorRazonSocial,
        String usuarioNombre,
        Long almacenId,
        String almacenNombre,
        Long monedaId,
        String monedaNombre,
        EstadoTransaccion estado,
        LocalDate fechaVencimiento,
        String observaciones,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        LocalDate fecha,
        List<DetalleCompraResponse> detalles) {

    public static CompraDetalleResponse from(Compra c, List<DetalleCompra> detalles) {
        return new CompraDetalleResponse(
                c.getId(),
                c.getTipoDocumento().getNombre(),
                c.getNumeroDocumento(),
                c.getProveedor().getId(),
                c.getProveedor().getRazonSocial(),
                c.getUsuario().getNombre() + " " + c.getUsuario().getApellidos(),
                c.getAlmacen().getId(),
                c.getAlmacen().getNombre(),
                c.getMoneda().getId(),
                c.getMoneda().getNombre(),
                c.getEstado(),
                c.getFechaVencimiento(),
                c.getObservaciones(),
                c.getSubtotal(),
                c.getIgv(),
                c.getTotal(),
                c.getFecha(),
                detalles.stream().map(DetalleCompraResponse::from).toList());
    }
}
