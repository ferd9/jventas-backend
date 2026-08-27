package com.jventas.backend.venta;

import com.jventas.backend.almacen.AccesoAlmacenService;
import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.almacen.AlmacenRepository;
import com.jventas.backend.catalogo.Impuesto;
import com.jventas.backend.catalogo.ImpuestoRepository;
import com.jventas.backend.catalogo.TipoDocumentoRepository;
import com.jventas.backend.cliente.ClienteRepository;
import com.jventas.backend.common.RangoFecha;
import com.jventas.backend.compra.EstadoTransaccion;
import com.jventas.backend.documento.SerieDocumentoService;
import com.jventas.backend.inventario.AlmacenStock;
import com.jventas.backend.inventario.AlmacenStockRepository;
import com.jventas.backend.inventario.Kardex;
import com.jventas.backend.inventario.KardexRepository;
import com.jventas.backend.inventario.TipoDocumentoKardex;
import com.jventas.backend.moneda.MonedaRepository;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.producto.ProductoRepository;
import com.jventas.backend.serieproducto.SerieProductoService;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Espejo de CompraService, con una diferencia real: acá sí hace falta
 * validar que haya stock suficiente antes de confirmar — una compra nunca
 * necesita esa validación, una venta siempre.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final ClienteRepository clienteRepository;
    private final AlmacenRepository almacenRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final ImpuestoRepository impuestoRepository;
    private final AlmacenStockRepository almacenStockRepository;
    private final KardexRepository kardexRepository;
    private final SerieDocumentoService serieDocumentoService;
    private final AccesoAlmacenService accesoAlmacenService;
    private final SerieProductoService serieProductoService;

    @Transactional(readOnly = true)
    public Page<VentaResumenResponse> listar(Long clienteId, LocalDate fechaDesde, LocalDate fechaHasta, Pageable pageable) {
        return ventaRepository
                .buscar(clienteId, RangoFecha.desde(fechaDesde), RangoFecha.hasta(fechaHasta), pageable)
                .map(VentaResumenResponse::from);
    }

    @Transactional(readOnly = true)
    public VentaDetalleResponse obtener(Long id) {
        Venta venta = obtenerEntidad(id);
        return VentaDetalleResponse.from(venta, detalleVentaRepository.findByVentaId(id));
    }

    @Transactional
    public VentaDetalleResponse crear(VentaRequest request, String loginUsuario) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, request.almacenId());

        Venta venta = new Venta();
        venta.setTipoDocumento(referencia(tipoDocumentoRepository, request.tipoDocumentoId(), "Tipo de documento"));
        venta.setCliente(referencia(clienteRepository, request.clienteId(), "Cliente"));
        venta.setAlmacen(referencia(almacenRepository, request.almacenId(), "Almacén"));
        venta.setMoneda(referencia(monedaRepository, request.monedaId(), "Moneda"));
        venta.setUsuario(usuario);
        venta.setFechaVencimiento(request.fechaVencimiento());
        venta.setObservaciones(request.observaciones());
        venta.setNumItems(request.detalles().size());

        if (request.serieDocumentoId() != null) {
            var serieDocumento = serieDocumentoService.consumirSiguiente(
                    request.serieDocumentoId(), request.almacenId(), request.tipoDocumentoId());
            venta.setSerieDocumento(serieDocumento);
            venta.setNumeroDocumento(serieDocumento.formatear(serieDocumento.getCorrelativoActual()));
        } else {
            venta.setNumeroDocumento(request.numeroDocumento());
        }

        venta = ventaRepository.save(venta);

        BigDecimal subtotalVenta = BigDecimal.ZERO;
        BigDecimal igvVenta = BigDecimal.ZERO;

        for (DetalleVentaRequest detalleRequest : request.detalles()) {
            Producto producto = referencia(productoRepository, detalleRequest.productoId(), "Producto");
            Impuesto impuesto = detalleRequest.impuestoId() != null
                    ? referencia(impuestoRepository, detalleRequest.impuestoId(), "Impuesto")
                    : null;

            BigDecimal descuentoPct = detalleRequest.descuentoPct() != null ? detalleRequest.descuentoPct() : BigDecimal.ZERO;
            BigDecimal factorDescuento = BigDecimal.ONE.subtract(descuentoPct.divide(BigDecimal.valueOf(100)));
            BigDecimal subtotalLinea = detalleRequest
                    .precioUnitario()
                    .multiply(BigDecimal.valueOf(detalleRequest.cantidad()))
                    .multiply(factorDescuento)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal montoImpuesto = impuesto != null
                    ? subtotalLinea.multiply(impuesto.getTasa()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (serieProductoService.requiereSerie(producto)) {
                validarNumerosSerie(producto, detalleRequest.numerosSerie(), detalleRequest.cantidad());
            }

            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setCantidad(detalleRequest.cantidad());
            detalle.setPrecioUnitario(detalleRequest.precioUnitario());
            detalle.setDescuentoPct(descuentoPct);
            detalle.setImpuesto(impuesto);
            detalle.setMontoImpuesto(montoImpuesto);
            detalle.setSubtotal(subtotalLinea);
            detalleVentaRepository.save(detalle);

            if (serieProductoService.requiereSerie(producto)) {
                serieProductoService.venderSeries(detalle, detalleRequest.numerosSerie(), venta.getAlmacen().getId(), venta.getCliente());
            }

            int nuevoStock = decrementarStockValidando(venta.getAlmacen(), producto, detalleRequest.cantidad());

            Kardex kardex = new Kardex();
            kardex.setAlmacen(venta.getAlmacen());
            kardex.setProducto(producto);
            kardex.setTipoDocumento(TipoDocumentoKardex.VENTA);
            kardex.setNumeroDocumento(venta.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setVenta(venta);
            kardex.setSalida(detalleRequest.cantidad());
            kardex.setPrecio(detalleRequest.precioUnitario());
            kardex.setValor(subtotalLinea);
            // vender no cambia el promedio -- solo lo lee. costo = lo que esta unidad
            // le costó a la empresa en promedio, no lo que se le cobró al cliente (precio)
            kardex.setCostoUnitario(producto.getCosto());
            kardex.setCostoTotal(producto.getCosto().multiply(BigDecimal.valueOf(detalleRequest.cantidad())));
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(subtotalLinea.add(montoImpuesto));
            kardexRepository.save(kardex);

            subtotalVenta = subtotalVenta.add(subtotalLinea);
            igvVenta = igvVenta.add(montoImpuesto);
        }

        venta.setSubtotal(subtotalVenta);
        venta.setIgv(igvVenta);
        venta.setTotal(subtotalVenta.add(igvVenta));
        venta = ventaRepository.save(venta);

        return VentaDetalleResponse.from(venta, detalleVentaRepository.findByVentaId(venta.getId()));
    }

    @Transactional
    public VentaDetalleResponse anular(Long id, String loginUsuario) {
        Venta venta = obtenerEntidad(id);
        if (venta.getEstado() == EstadoTransaccion.ANULADO) {
            throw new IllegalArgumentException("La venta " + id + " ya está anulada");
        }
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, venta.getAlmacen().getId());

        for (DetalleVenta detalle : detalleVentaRepository.findByVentaId(id)) {
            serieProductoService.revertirPorAnularVenta(detalle);

            int nuevoStock = incrementarStock(venta.getAlmacen(), detalle.getProducto(), detalle.getCantidad());

            // costo vigente al momento de anular, no el que tenía al vender -- venta nunca
            // guardó ese dato en producto.costo (vender no cambia el promedio), así que no
            // hay forma exacta de recuperarlo; es la misma aproximación que acepta el costeo
            // por promedio en general (ver CosteoPromedioPonderadoService)
            BigDecimal costoVigente = detalle.getProducto().getCosto();

            Kardex kardex = new Kardex();
            kardex.setAlmacen(venta.getAlmacen());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.PRODUCTO_ELIMINADO_VENTA);
            kardex.setNumeroDocumento(venta.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setVenta(venta);
            kardex.setEntrada(detalle.getCantidad());
            kardex.setPrecio(detalle.getPrecioUnitario());
            kardex.setValor(detalle.getSubtotal());
            kardex.setCostoUnitario(costoVigente);
            kardex.setCostoTotal(costoVigente.multiply(BigDecimal.valueOf(detalle.getCantidad())));
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(detalle.getSubtotal().add(detalle.getMontoImpuesto()));
            kardexRepository.save(kardex);
        }

        venta.setEstado(EstadoTransaccion.ANULADO);
        venta = ventaRepository.save(venta);
        return VentaDetalleResponse.from(venta, detalleVentaRepository.findByVentaId(id));
    }

    private void validarNumerosSerie(Producto producto, List<String> numerosSerie, int cantidad) {
        if (numerosSerie == null || numerosSerie.size() != cantidad) {
            throw new IllegalArgumentException(
                    producto.getNombre() + " exige número de serie por unidad: se esperaban " + cantidad + " número(s) de serie");
        }
    }

    private int decrementarStockValidando(Almacen almacen, Producto producto, int cantidadSolicitada) {
        AlmacenStock stock = almacenStockRepository
                .findByAlmacenIdAndProductoIdParaActualizar(almacen.getId(), producto.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay stock de " + producto.getNombre() + " en " + almacen.getNombre()));

        if (stock.getCantidadActual() < cantidadSolicitada) {
            throw new IllegalArgumentException("Stock insuficiente de " + producto.getNombre() + " en " + almacen.getNombre()
                    + ": disponible " + stock.getCantidadActual() + ", solicitado " + cantidadSolicitada);
        }

        stock.setCantidadActual(stock.getCantidadActual() - cantidadSolicitada);
        return almacenStockRepository.save(stock).getCantidadActual();
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

    private Venta obtenerEntidad(Long id) {
        return ventaRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Venta no encontrada: " + id));
    }

    private <T, ID> T referencia(JpaRepository<T, ID> repo, ID id, String nombreEntidad) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException(nombreEntidad + " no encontrado: " + id));
    }
}
