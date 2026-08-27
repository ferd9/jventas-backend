package com.jventas.backend.pago;

import com.jventas.backend.catalogo.MetodoPago;
import com.jventas.backend.catalogo.MetodoPagoRepository;
import com.jventas.backend.compra.Compra;
import com.jventas.backend.compra.CompraRepository;
import com.jventas.backend.compra.EstadoTransaccion;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import com.jventas.backend.venta.Venta;
import com.jventas.backend.venta.VentaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "CANCELADO" en compra/venta significa pagado, no anulado (uso original en
 * español de negocio). Cuando el saldo llega a 0, el estado pasa solo.
 */
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final CompraRepository compraRepository;
    private final VentaRepository ventaRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<PagoResponse> listarPorCompra(Long compraId) {
        return pagoRepository.findByCompraIdAndActivoTrue(compraId).stream().map(PagoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponse> listarPorVenta(Long ventaId) {
        return pagoRepository.findByVentaIdAndActivoTrue(ventaId).stream().map(PagoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SaldoResponse saldoDeCompra(Long compraId) {
        Compra compra = compraRepository.findById(compraId).orElseThrow(() -> new NoSuchElementException("Compra no encontrada: " + compraId));
        BigDecimal pagado = pagoRepository.sumarPagadoDeCompra(compraId);
        return new SaldoResponse(compra.getTotal(), pagado, compra.getTotal().subtract(pagado));
    }

    @Transactional(readOnly = true)
    public SaldoResponse saldoDeVenta(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + ventaId));
        BigDecimal pagado = pagoRepository.sumarPagadoDeVenta(ventaId);
        return new SaldoResponse(venta.getTotal(), pagado, venta.getTotal().subtract(pagado));
    }

    @Transactional
    public PagoResponse registrar(PagoRequest request, String loginUsuario) {
        if (!request.esOrigenValido()) {
            throw new IllegalArgumentException("El pago debe indicar una compra o una venta, no ambas ni ninguna");
        }

        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        MetodoPago metodoPago = metodoPagoRepository
                .findById(request.metodoPagoId())
                .orElseThrow(() -> new NoSuchElementException("Método de pago no encontrado: " + request.metodoPagoId()));

        Pago pago = new Pago();
        pago.setMetodoPago(metodoPago);
        pago.setUsuario(usuario);
        pago.setMonto(request.monto());
        pago.setReferencia(request.referencia());

        if (request.compraId() != null) {
            Compra compra = compraRepository
                    .findById(request.compraId())
                    .orElseThrow(() -> new NoSuchElementException("Compra no encontrada: " + request.compraId()));
            validarPuedeRecibirPago(compra.getEstado(), "compra");
            BigDecimal saldo = compra.getTotal().subtract(pagoRepository.sumarPagadoDeCompra(compra.getId()));
            validarNoExcedeSaldo(request.monto(), saldo);

            pago.setCompra(compra);
            pago = pagoRepository.save(pago);

            if (saldo.subtract(request.monto()).compareTo(BigDecimal.ZERO) <= 0) {
                compra.setEstado(EstadoTransaccion.CANCELADO);
                compraRepository.save(compra);
            }
        } else {
            Venta venta = ventaRepository
                    .findById(request.ventaId())
                    .orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + request.ventaId()));
            validarPuedeRecibirPago(venta.getEstado(), "venta");
            BigDecimal saldo = venta.getTotal().subtract(pagoRepository.sumarPagadoDeVenta(venta.getId()));
            validarNoExcedeSaldo(request.monto(), saldo);

            pago.setVenta(venta);
            pago = pagoRepository.save(pago);

            if (saldo.subtract(request.monto()).compareTo(BigDecimal.ZERO) <= 0) {
                venta.setEstado(EstadoTransaccion.CANCELADO);
                ventaRepository.save(venta);
            }
        }

        return PagoResponse.from(pago);
    }

    private void validarPuedeRecibirPago(EstadoTransaccion estado, String tipo) {
        if (estado == EstadoTransaccion.ANULADO) {
            throw new IllegalArgumentException("No se puede registrar un pago sobre una " + tipo + " anulada");
        }
    }

    private void validarNoExcedeSaldo(BigDecimal monto, BigDecimal saldo) {
        if (monto.compareTo(saldo) > 0) {
            throw new IllegalArgumentException("El pago (" + monto + ") excede el saldo pendiente (" + saldo + ")");
        }
    }
}
