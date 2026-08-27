package com.jventas.backend.venta;

import com.jventas.backend.compra.EstadoTransaccion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaResumenResponse(
        Long id, String numeroDocumento, String clienteNombre, EstadoTransaccion estado, BigDecimal total, LocalDate fecha) {

    public static VentaResumenResponse from(Venta v) {
        return new VentaResumenResponse(
                v.getId(),
                v.getNumeroDocumento(),
                v.getCliente().getNombre() + " " + v.getCliente().getApellidos(),
                v.getEstado(),
                v.getTotal(),
                v.getFecha());
    }
}
