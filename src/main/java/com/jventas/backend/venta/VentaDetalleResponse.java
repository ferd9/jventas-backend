package com.jventas.backend.venta;

import com.jventas.backend.compra.EstadoTransaccion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VentaDetalleResponse(
        Long id,
        String tipoDocumentoNombre,
        String numeroDocumento,
        Long clienteId,
        String clienteNombre,
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
        List<DetalleVentaResponse> detalles) {

    public static VentaDetalleResponse from(Venta v, List<DetalleVenta> detalles) {
        return new VentaDetalleResponse(
                v.getId(),
                v.getTipoDocumento().getNombre(),
                v.getNumeroDocumento(),
                v.getCliente().getId(),
                v.getCliente().getNombre() + " " + v.getCliente().getApellidos(),
                v.getUsuario().getNombre() + " " + v.getUsuario().getApellidos(),
                v.getAlmacen().getId(),
                v.getAlmacen().getNombre(),
                v.getMoneda().getId(),
                v.getMoneda().getNombre(),
                v.getEstado(),
                v.getFechaVencimiento(),
                v.getObservaciones(),
                v.getSubtotal(),
                v.getIgv(),
                v.getTotal(),
                v.getFecha(),
                detalles.stream().map(DetalleVentaResponse::from).toList());
    }
}
