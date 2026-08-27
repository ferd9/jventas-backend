package com.jventas.backend.reporte;

import com.jventas.backend.compra.Compra;
import com.jventas.backend.compra.CompraRepository;
import com.jventas.backend.pago.PagoRepository;
import com.jventas.backend.pago.SaldoAgrupado;
import com.jventas.backend.venta.Venta;
import com.jventas.backend.venta.VentaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Cuentas por cobrar/pagar" son las compras/ventas con estado PENDIENTE --
 * las CANCELADAS ya se saldaron por completo y las ANULADAS no cuentan. El
 * saldo de cada una se calcula en un solo query agrupado (no N+1) contra
 * PagoRepository.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final PagoRepository pagoRepository;

    @Transactional(readOnly = true)
    public List<CuentaPorCobrarResponse> cuentasPorCobrar() {
        List<Venta> ventas = ventaRepository.findPendientesConCliente();
        Map<Long, BigDecimal> pagadoPorVenta = ventas.isEmpty()
                ? Map.of()
                : pagosPorDocumento(pagoRepository.sumarPagadoPorVentas(ventas.stream().map(Venta::getId).toList()));

        LocalDate hoy = LocalDate.now();
        return ventas.stream()
                .map(venta -> {
                    BigDecimal pagado = pagadoPorVenta.getOrDefault(venta.getId(), BigDecimal.ZERO);
                    boolean vencido = venta.getFechaVencimiento() != null && venta.getFechaVencimiento().isBefore(hoy);
                    return new CuentaPorCobrarResponse(
                            venta.getId(),
                            venta.getNumeroDocumento(),
                            venta.getCliente().getId(),
                            venta.getCliente().getNombre() + " " + venta.getCliente().getApellidos(),
                            venta.getFecha(),
                            venta.getFechaVencimiento(),
                            vencido,
                            venta.getTotal(),
                            pagado,
                            venta.getTotal().subtract(pagado));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CuentaPorPagarResponse> cuentasPorPagar() {
        List<Compra> compras = compraRepository.findPendientesConProveedor();
        Map<Long, BigDecimal> pagadoPorCompra = compras.isEmpty()
                ? Map.of()
                : pagosPorDocumento(pagoRepository.sumarPagadoPorCompras(compras.stream().map(Compra::getId).toList()));

        LocalDate hoy = LocalDate.now();
        return compras.stream()
                .map(compra -> {
                    BigDecimal pagado = pagadoPorCompra.getOrDefault(compra.getId(), BigDecimal.ZERO);
                    boolean vencido = compra.getFechaVencimiento() != null && compra.getFechaVencimiento().isBefore(hoy);
                    return new CuentaPorPagarResponse(
                            compra.getId(),
                            compra.getNumeroDocumento(),
                            compra.getProveedor().getId(),
                            compra.getProveedor().getRazonSocial(),
                            compra.getFecha(),
                            compra.getFechaVencimiento(),
                            vencido,
                            compra.getTotal(),
                            pagado,
                            compra.getTotal().subtract(pagado));
                })
                .toList();
    }

    private Map<Long, BigDecimal> pagosPorDocumento(List<SaldoAgrupado> filas) {
        return filas.stream().collect(Collectors.toMap(SaldoAgrupado::getDocumentoId, SaldoAgrupado::getPagado));
    }
}
