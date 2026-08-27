package com.jventas.backend.devolucion;

import com.jventas.backend.almacen.AccesoAlmacenService;
import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.compra.Compra;
import com.jventas.backend.compra.CompraRepository;
import com.jventas.backend.compra.DetalleCompra;
import com.jventas.backend.compra.DetalleCompraRepository;
import com.jventas.backend.compra.EstadoTransaccion;
import com.jventas.backend.inventario.AlmacenStock;
import com.jventas.backend.inventario.AlmacenStockRepository;
import com.jventas.backend.inventario.CosteoPromedioPonderadoService;
import com.jventas.backend.inventario.Kardex;
import com.jventas.backend.inventario.KardexRepository;
import com.jventas.backend.inventario.TipoDocumentoKardex;
import com.jventas.backend.pago.PagoRepository;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.serieproducto.SerieProductoService;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Espejo de DevolucionService (venta), del lado de compra -- mismo plazo de
 * 10 días, mismo diseño de documento propio. Las diferencias reales: acá el
 * stock SALE (vuelve al proveedor, no al cliente) y hay que validar que
 * siga disponible -- si ya se vendió, no se puede devolver -- y revertir el
 * costo promedio ponderado, porque esas unidades salen del cálculo (mismo
 * criterio que anular una compra).
 */
@Service
@RequiredArgsConstructor
public class DevolucionCompraService {

    private static final int PLAZO_DIAS = 10;

    private final CompraRepository compraRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    private final DevolucionCompraRepository devolucionCompraRepository;
    private final DetalleDevolucionCompraRepository detalleDevolucionCompraRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenStockRepository almacenStockRepository;
    private final KardexRepository kardexRepository;
    private final SerieProductoService serieProductoService;
    private final CosteoPromedioPonderadoService costeoService;
    private final AccesoAlmacenService accesoAlmacenService;
    private final PagoRepository pagoRepository;

    @Transactional
    public DevolucionCompraResponse registrar(Long compraId, DevolucionCompraRequest request, String loginUsuario) {
        Compra compra =
                compraRepository.findById(compraId).orElseThrow(() -> new NoSuchElementException("Compra no encontrada: " + compraId));
        if (compra.getEstado() == EstadoTransaccion.ANULADO) {
            throw new IllegalArgumentException("No se puede devolver de una compra anulada");
        }
        if (LocalDate.now().isAfter(compra.getFecha().plusDays(PLAZO_DIAS))) {
            throw new IllegalArgumentException(
                    "El plazo para devolver (" + PLAZO_DIAS + " días desde la compra, " + compra.getFecha() + ") ya venció");
        }

        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, compra.getAlmacen().getId());

        DevolucionCompra devolucion = new DevolucionCompra();
        devolucion.setCompra(compra);
        devolucion.setUsuario(usuario);
        devolucion.setMotivo(request.motivo());
        devolucion.setMontoTotal(BigDecimal.ZERO);
        devolucion = devolucionCompraRepository.save(devolucion);

        BigDecimal subtotalADescontar = BigDecimal.ZERO;
        BigDecimal igvADescontar = BigDecimal.ZERO;

        for (DevolucionCompraLineaRequest linea : request.lineas()) {
            DetalleCompra detalle = detalleCompraRepository
                    .findByIdParaActualizar(linea.detalleCompraId())
                    .orElseThrow(() -> new NoSuchElementException("Línea de compra no encontrada: " + linea.detalleCompraId()));

            if (!detalle.getCompra().getId().equals(compraId)) {
                throw new IllegalArgumentException("La línea " + linea.detalleCompraId() + " no pertenece a esta compra");
            }

            int yaDevuelto = detalleDevolucionCompraRepository.sumarCantidadDevuelta(detalle.getId());
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
                serieProductoService.revertirParcialPorDevolucionCompra(detalle, linea.numerosSerie());
            }

            // proporcional por unidad sobre lo que efectivamente se compró en esa línea (con su descuento ya aplicado)
            BigDecimal montoUnitario = detalle.getSubtotal().divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            BigDecimal montoImpuestoUnitario =
                    detalle.getMontoImpuesto().divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            BigDecimal montoLinea = montoUnitario.multiply(BigDecimal.valueOf(linea.cantidad()));
            BigDecimal montoImpuestoLinea = montoImpuestoUnitario.multiply(BigDecimal.valueOf(linea.cantidad()));

            DetalleDevolucionCompra detalleDevolucion = new DetalleDevolucionCompra();
            detalleDevolucion.setDevolucionCompra(devolucion);
            detalleDevolucion.setDetalleCompra(detalle);
            detalleDevolucion.setCantidad(linea.cantidad());
            detalleDevolucion.setMonto(montoLinea);
            detalleDevolucion.setMontoImpuesto(montoImpuestoLinea);
            detalleDevolucionCompraRepository.save(detalleDevolucion);

            // el costo promedio se revierte con el stock ANTES de decrementarlo -- mismo orden que anular() compra
            costeoService.revertirCompra(detalle.getProducto().getId(), linea.cantidad(), detalle.getPrecioUnitario());

            int nuevoStock = decrementarStockValidando(compra.getAlmacen(), detalle.getProducto(), linea.cantidad());

            Kardex kardex = new Kardex();
            kardex.setAlmacen(compra.getAlmacen());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.DEVOLUCION_COMPRA);
            kardex.setNumeroDocumento(compra.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setCompra(compra);
            kardex.setSalida(linea.cantidad());
            kardex.setPrecio(detalle.getPrecioUnitario());
            kardex.setValor(montoLinea);
            // igual que en COMPRA: el costo de este movimiento es lo que se pagó por esas unidades
            kardex.setCostoUnitario(detalle.getPrecioUnitario());
            kardex.setCostoTotal(detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(linea.cantidad())));
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(montoLinea.add(montoImpuestoLinea));
            kardexRepository.save(kardex);

            subtotalADescontar = subtotalADescontar.add(montoLinea);
            igvADescontar = igvADescontar.add(montoImpuestoLinea);
        }

        compra.setSubtotal(compra.getSubtotal().subtract(subtotalADescontar));
        compra.setIgv(compra.getIgv().subtract(igvADescontar));
        compra.setTotal(compra.getTotal().subtract(subtotalADescontar).subtract(igvADescontar));

        // si la devolución deja el saldo en 0 (o menos), la compra pasa a CANCELADO -- mismo criterio que PagoService/DevolucionService
        BigDecimal pagado = pagoRepository.sumarPagadoDeCompra(compra.getId());
        if (compra.getEstado() != EstadoTransaccion.ANULADO && compra.getTotal().subtract(pagado).compareTo(BigDecimal.ZERO) <= 0) {
            compra.setEstado(EstadoTransaccion.CANCELADO);
        }
        compraRepository.save(compra);

        devolucion.setMontoTotal(subtotalADescontar.add(igvADescontar));
        devolucion = devolucionCompraRepository.save(devolucion);

        return DevolucionCompraResponse.from(devolucion, detalleDevolucionCompraRepository.findByDevolucionCompraId(devolucion.getId()));
    }

    @Transactional(readOnly = true)
    public List<DevolucionCompraResponse> listar(Long compraId) {
        return devolucionCompraRepository.findByCompraIdOrderByFechaDesc(compraId).stream()
                .map(d -> DevolucionCompraResponse.from(d, detalleDevolucionCompraRepository.findByDevolucionCompraId(d.getId())))
                .toList();
    }

    private int decrementarStockValidando(Almacen almacen, Producto producto, int cantidadSolicitada) {
        AlmacenStock stock = almacenStockRepository
                .findByAlmacenIdAndProductoIdParaActualizar(almacen.getId(), producto.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay stock de " + producto.getNombre() + " en " + almacen.getNombre() + " para devolver"));

        if (stock.getCantidadActual() < cantidadSolicitada) {
            throw new IllegalArgumentException("No se puede devolver " + cantidadSolicitada + " unidad(es) de " + producto.getNombre()
                    + ": solo hay " + stock.getCantidadActual() + " en stock (el resto ya se vendió o trasladó)");
        }

        stock.setCantidadActual(stock.getCantidadActual() - cantidadSolicitada);
        return almacenStockRepository.save(stock).getCantidadActual();
    }
}
