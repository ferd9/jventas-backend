package com.jventas.backend.devolucion;

import com.jventas.backend.almacen.AccesoAlmacenService;
import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.compra.EstadoTransaccion;
import com.jventas.backend.inventario.AlmacenStock;
import com.jventas.backend.inventario.AlmacenStockRepository;
import com.jventas.backend.inventario.Kardex;
import com.jventas.backend.inventario.KardexRepository;
import com.jventas.backend.inventario.TipoDocumentoKardex;
import com.jventas.backend.pago.PagoRepository;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.serieproducto.SerieProductoService;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import com.jventas.backend.venta.DetalleVenta;
import com.jventas.backend.venta.DetalleVentaRepository;
import com.jventas.backend.venta.Venta;
import com.jventas.backend.venta.VentaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisión de negocio confirmada: devolución parcial de una venta, plazo de
 * 10 días desde la fecha de venta. Documento propio (no edita la venta
 * original) -- el stock devuelto vuelve automático a disponible sin
 * revisión previa, y el reembolso se descuenta directo del total de la
 * venta (mismo criterio que un pago, pero en sentido contrario).
 */
@Service
@RequiredArgsConstructor
public class DevolucionService {

    private static final int PLAZO_DIAS = 10;

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final DevolucionRepository devolucionRepository;
    private final DetalleDevolucionRepository detalleDevolucionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenStockRepository almacenStockRepository;
    private final KardexRepository kardexRepository;
    private final SerieProductoService serieProductoService;
    private final AccesoAlmacenService accesoAlmacenService;
    private final PagoRepository pagoRepository;

    @Transactional
    public DevolucionResponse registrar(Long ventaId, DevolucionRequest request, String loginUsuario) {
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + ventaId));
        if (venta.getEstado() == EstadoTransaccion.ANULADO) {
            throw new IllegalArgumentException("No se puede devolver de una venta anulada");
        }
        if (LocalDate.now().isAfter(venta.getFecha().plusDays(PLAZO_DIAS))) {
            throw new IllegalArgumentException(
                    "El plazo para devolver (" + PLAZO_DIAS + " días desde la venta, " + venta.getFecha() + ") ya venció");
        }

        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, venta.getAlmacen().getId());

        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(venta);
        devolucion.setUsuario(usuario);
        devolucion.setMotivo(request.motivo());
        devolucion.setMontoTotal(BigDecimal.ZERO);
        devolucion = devolucionRepository.save(devolucion);

        BigDecimal subtotalADescontar = BigDecimal.ZERO;
        BigDecimal igvADescontar = BigDecimal.ZERO;

        for (DevolucionLineaRequest linea : request.lineas()) {
            DetalleVenta detalle = detalleVentaRepository
                    .findByIdParaActualizar(linea.detalleVentaId())
                    .orElseThrow(() -> new NoSuchElementException("Línea de venta no encontrada: " + linea.detalleVentaId()));

            if (!detalle.getVenta().getId().equals(ventaId)) {
                throw new IllegalArgumentException("La línea " + linea.detalleVentaId() + " no pertenece a esta venta");
            }

            int yaDevuelto = detalleDevolucionRepository.sumarCantidadDevuelta(detalle.getId());
            int disponibleParaDevolver = detalle.getCantidad() - yaDevuelto;
            if (linea.cantidad() > disponibleParaDevolver) {
                throw new IllegalArgumentException("Solo se puede(n) devolver " + disponibleParaDevolver + " unidad(es) de "
                        + detalle.getProducto().getNombre() + " (ya se devolvieron " + yaDevuelto + " de " + detalle.getCantidad() + ")");
            }

            if (serieProductoService.requiereSerie(detalle.getProducto())) {
                if (linea.numerosSerie() == null || linea.numerosSerie().size() != linea.cantidad()) {
                    throw new IllegalArgumentException(detalle.getProducto().getNombre() + " exige número de serie: se esperaban "
                            + linea.cantidad() + " número(s) de serie");
                }
                serieProductoService.revertirParcialPorDevolucion(detalle, linea.numerosSerie());
            }

            // proporcional por unidad sobre lo que efectivamente se vendió en esa línea (con su descuento ya aplicado)
            BigDecimal montoUnitario = detalle.getSubtotal().divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            BigDecimal montoImpuestoUnitario =
                    detalle.getMontoImpuesto().divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            BigDecimal montoLinea = montoUnitario.multiply(BigDecimal.valueOf(linea.cantidad()));
            BigDecimal montoImpuestoLinea = montoImpuestoUnitario.multiply(BigDecimal.valueOf(linea.cantidad()));

            DetalleDevolucion detalleDevolucion = new DetalleDevolucion();
            detalleDevolucion.setDevolucion(devolucion);
            detalleDevolucion.setDetalleVenta(detalle);
            detalleDevolucion.setCantidad(linea.cantidad());
            detalleDevolucion.setMonto(montoLinea);
            detalleDevolucion.setMontoImpuesto(montoImpuestoLinea);
            detalleDevolucionRepository.save(detalleDevolucion);

            int nuevoStock = incrementarStock(venta.getAlmacen(), detalle.getProducto(), linea.cantidad());
            BigDecimal costoVigente = detalle.getProducto().getCosto();

            Kardex kardex = new Kardex();
            kardex.setAlmacen(venta.getAlmacen());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.DEVOLUCION_VENTA);
            kardex.setNumeroDocumento(venta.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setVenta(venta);
            kardex.setEntrada(linea.cantidad());
            kardex.setPrecio(detalle.getPrecioUnitario());
            kardex.setValor(montoLinea);
            kardex.setCostoUnitario(costoVigente);
            kardex.setCostoTotal(costoVigente.multiply(BigDecimal.valueOf(linea.cantidad())));
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(montoLinea.add(montoImpuestoLinea));
            kardexRepository.save(kardex);

            subtotalADescontar = subtotalADescontar.add(montoLinea);
            igvADescontar = igvADescontar.add(montoImpuestoLinea);
        }

        venta.setSubtotal(venta.getSubtotal().subtract(subtotalADescontar));
        venta.setIgv(venta.getIgv().subtract(igvADescontar));
        venta.setTotal(venta.getTotal().subtract(subtotalADescontar).subtract(igvADescontar));

        // si la devolución deja el saldo en 0 (o menos, sobrepagado), la venta pasa a
        // CANCELADO -- mismo criterio que PagoService, que sin esto es el único lugar
        // que hace esa transición y "cuentas por cobrar" seguía listando esta venta
        // como pendiente aunque ya no se le debiera nada
        BigDecimal pagado = pagoRepository.sumarPagadoDeVenta(venta.getId());
        if (venta.getEstado() != EstadoTransaccion.ANULADO && venta.getTotal().subtract(pagado).compareTo(BigDecimal.ZERO) <= 0) {
            venta.setEstado(EstadoTransaccion.CANCELADO);
        }
        ventaRepository.save(venta);

        devolucion.setMontoTotal(subtotalADescontar.add(igvADescontar));
        devolucion = devolucionRepository.save(devolucion);

        return DevolucionResponse.from(devolucion, detalleDevolucionRepository.findByDevolucionId(devolucion.getId()));
    }

    @Transactional(readOnly = true)
    public List<DevolucionResponse> listar(Long ventaId) {
        return devolucionRepository.findByVentaIdOrderByFechaDesc(ventaId).stream()
                .map(d -> DevolucionResponse.from(d, detalleDevolucionRepository.findByDevolucionId(d.getId())))
                .toList();
    }

    private int incrementarStock(Almacen almacen, Producto producto, int cantidad) {
        AlmacenStock stock = almacenStockRepository
                .findByAlmacenIdAndProductoIdParaActualizar(almacen.getId(), producto.getId())
                .orElseGet(() -> {
                    AlmacenStock nuevo = new AlmacenStock();
                    nuevo.setAlmacen(almacen);
                    nuevo.setProducto(producto);
                    nuevo.setCantidadActual(0);
                    return nuevo;
                });
        stock.setCantidadActual(stock.getCantidadActual() + cantidad);
        return almacenStockRepository.save(stock).getCantidadActual();
    }
}
