package com.jventas.backend.compra;

import com.jventas.backend.almacen.AccesoAlmacenService;
import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.almacen.AlmacenRepository;
import com.jventas.backend.catalogo.Impuesto;
import com.jventas.backend.catalogo.ImpuestoRepository;
import com.jventas.backend.catalogo.TipoDocumento;
import com.jventas.backend.catalogo.TipoDocumentoRepository;
import com.jventas.backend.common.RangoFecha;
import com.jventas.backend.documento.SerieDocumentoService;
import com.jventas.backend.inventario.AlmacenStock;
import com.jventas.backend.inventario.AlmacenStockRepository;
import com.jventas.backend.inventario.CosteoPromedioPonderadoService;
import com.jventas.backend.inventario.Kardex;
import com.jventas.backend.inventario.KardexRepository;
import com.jventas.backend.inventario.TipoDocumentoKardex;
import com.jventas.backend.moneda.Moneda;
import com.jventas.backend.moneda.MonedaRepository;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.producto.ProductoRepository;
import com.jventas.backend.proveedor.Proveedor;
import com.jventas.backend.proveedor.ProveedorRepository;
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
 * Compra confirmada -> incrementa almacen_stock -> deja kardex de entrada.
 * Anular una compra hace exactamente lo inverso, con su propio kardex de
 * salida — nunca se borra el registro, siempre queda el rastro.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final ProveedorRepository proveedorRepository;
    private final AlmacenRepository almacenRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final ImpuestoRepository impuestoRepository;
    private final AlmacenStockRepository almacenStockRepository;
    private final KardexRepository kardexRepository;
    private final SerieDocumentoService serieDocumentoService;
    private final AccesoAlmacenService accesoAlmacenService;
    private final CosteoPromedioPonderadoService costeoService;
    private final SerieProductoService serieProductoService;

    @Transactional(readOnly = true)
    public Page<CompraResumenResponse> listar(Long proveedorId, LocalDate fechaDesde, LocalDate fechaHasta, Pageable pageable) {
        return compraRepository
                .buscar(proveedorId, RangoFecha.desde(fechaDesde), RangoFecha.hasta(fechaHasta), pageable)
                .map(CompraResumenResponse::from);
    }

    @Transactional(readOnly = true)
    public CompraDetalleResponse obtener(Long id) {
        Compra compra = obtenerEntidad(id);
        return CompraDetalleResponse.from(compra, detalleCompraRepository.findByCompraId(id));
    }

    @Transactional
    public CompraDetalleResponse crear(CompraRequest request, String loginUsuario) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, request.almacenId());

        Compra compra = new Compra();
        compra.setTipoDocumento(referencia(tipoDocumentoRepository, request.tipoDocumentoId(), "Tipo de documento"));
        compra.setProveedor(referencia(proveedorRepository, request.proveedorId(), "Proveedor"));
        compra.setAlmacen(referencia(almacenRepository, request.almacenId(), "Almacén"));
        compra.setMoneda(referencia(monedaRepository, request.monedaId(), "Moneda"));
        compra.setUsuario(usuario);
        compra.setFechaVencimiento(request.fechaVencimiento());
        compra.setObservaciones(request.observaciones());
        compra.setNumItems(request.detalles().size());

        if (request.serieDocumentoId() != null) {
            var serieDocumento = serieDocumentoService.consumirSiguiente(
                    request.serieDocumentoId(), request.almacenId(), request.tipoDocumentoId());
            compra.setSerieDocumento(serieDocumento);
            compra.setNumeroDocumento(serieDocumento.formatear(serieDocumento.getCorrelativoActual()));
        } else {
            compra.setNumeroDocumento(request.numeroDocumento());
        }

        compra = compraRepository.save(compra);

        BigDecimal subtotalCompra = BigDecimal.ZERO;
        BigDecimal igvCompra = BigDecimal.ZERO;

        for (DetalleCompraRequest detalleRequest : request.detalles()) {
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

            DetalleCompra detalle = new DetalleCompra();
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setCantidad(detalleRequest.cantidad());
            detalle.setPrecioUnitario(detalleRequest.precioUnitario());
            detalle.setDescuentoPct(descuentoPct);
            detalle.setImpuesto(impuesto);
            detalle.setMontoImpuesto(montoImpuesto);
            detalle.setSubtotal(subtotalLinea);
            detalleCompraRepository.save(detalle);

            if (serieProductoService.requiereSerie(producto)) {
                serieProductoService.registrarPorCompra(detalle, detalleRequest.numerosSerie(), compra.getAlmacen(), compra.getProveedor());
            }

            // el costo promedio se recalcula con el stock ANTES de esta compra --
            // tiene que ir antes de incrementarStock(), no después
            costeoService.registrarCompra(producto.getId(), detalleRequest.cantidad(), detalleRequest.precioUnitario());

            int nuevoStock = incrementarStock(compra.getAlmacen(), producto, detalleRequest.cantidad());

            BigDecimal costoTotalLinea = detalleRequest.precioUnitario().multiply(BigDecimal.valueOf(detalleRequest.cantidad()));

            Kardex kardex = new Kardex();
            kardex.setAlmacen(compra.getAlmacen());
            kardex.setProducto(producto);
            kardex.setTipoDocumento(TipoDocumentoKardex.COMPRA);
            kardex.setNumeroDocumento(compra.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setCompra(compra);
            kardex.setEntrada(detalleRequest.cantidad());
            kardex.setPrecio(detalleRequest.precioUnitario());
            kardex.setValor(subtotalLinea);
            // en COMPRA el costo de este movimiento es lo que se pagó -- coincide con precio/valor a propósito
            kardex.setCostoUnitario(detalleRequest.precioUnitario());
            kardex.setCostoTotal(costoTotalLinea);
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(subtotalLinea.add(montoImpuesto));
            kardexRepository.save(kardex);

            subtotalCompra = subtotalCompra.add(subtotalLinea);
            igvCompra = igvCompra.add(montoImpuesto);
        }

        compra.setSubtotal(subtotalCompra);
        compra.setIgv(igvCompra);
        compra.setTotal(subtotalCompra.add(igvCompra));
        compra = compraRepository.save(compra);

        return CompraDetalleResponse.from(compra, detalleCompraRepository.findByCompraId(compra.getId()));
    }

    @Transactional
    public CompraDetalleResponse anular(Long id, String loginUsuario) {
        Compra compra = obtenerEntidad(id);
        if (compra.getEstado() == EstadoTransaccion.ANULADO) {
            throw new IllegalArgumentException("La compra " + id + " ya está anulada");
        }
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, compra.getAlmacen().getId());

        for (DetalleCompra detalle : detalleCompraRepository.findByCompraId(id)) {
            // revertir el promedio ponderado con el stock ANTES de decrementarStock()
            costeoService.revertirCompra(detalle.getProducto().getId(), detalle.getCantidad(), detalle.getPrecioUnitario());
            serieProductoService.revertirPorAnularCompra(detalle);

            int nuevoStock = decrementarStock(compra.getAlmacen(), detalle.getProducto(), detalle.getCantidad());

            Kardex kardex = new Kardex();
            kardex.setAlmacen(compra.getAlmacen());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.PRODUCTO_ELIMINADO_COMPRA);
            kardex.setNumeroDocumento(compra.getNumeroDocumento());
            kardex.setUsuario(usuario);
            kardex.setCompra(compra);
            kardex.setSalida(detalle.getCantidad());
            kardex.setPrecio(detalle.getPrecioUnitario());
            kardex.setValor(detalle.getSubtotal());
            kardex.setCostoUnitario(detalle.getPrecioUnitario());
            kardex.setCostoTotal(detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad())));
            kardex.setStockResultante(nuevoStock);
            kardex.setValorTotal(detalle.getSubtotal().add(detalle.getMontoImpuesto()));
            kardexRepository.save(kardex);
        }

        compra.setEstado(EstadoTransaccion.ANULADO);
        compra = compraRepository.save(compra);
        return CompraDetalleResponse.from(compra, detalleCompraRepository.findByCompraId(id));
    }

    private void validarNumerosSerie(Producto producto, List<String> numerosSerie, int cantidad) {
        if (numerosSerie == null || numerosSerie.size() != cantidad) {
            throw new IllegalArgumentException(
                    producto.getNombre() + " exige número de serie por unidad: se esperaban " + cantidad + " número(s) de serie");
        }
        if (numerosSerie.stream().anyMatch(n -> n == null || n.isBlank())) {
            throw new IllegalArgumentException("Los números de serie de " + producto.getNombre() + " no pueden estar vacíos");
        }
        if (numerosSerie.stream().distinct().count() != numerosSerie.size()) {
            throw new IllegalArgumentException("Los números de serie de " + producto.getNombre() + " no pueden repetirse en la misma compra");
        }
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

    private int decrementarStock(Almacen almacen, Producto producto, int cantidad) {
        AlmacenStock stock = almacenStockRepository
                .findByAlmacenIdAndProductoIdParaActualizar(almacen.getId(), producto.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay stock registrado de " + producto.getNombre() + " en este almacén"));
        stock.setCantidadActual(stock.getCantidadActual() - cantidad);
        return almacenStockRepository.save(stock).getCantidadActual();
    }

    private Compra obtenerEntidad(Long id) {
        return compraRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Compra no encontrada: " + id));
    }

    private <T, ID> T referencia(JpaRepository<T, ID> repo, ID id, String nombreEntidad) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException(nombreEntidad + " no encontrado: " + id));
    }
}
