package com.jventas.backend.traslado;

import com.jventas.backend.almacen.AccesoAlmacenService;
import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.almacen.AlmacenRepository;
import com.jventas.backend.inventario.AlmacenStock;
import com.jventas.backend.inventario.AlmacenStockRepository;
import com.jventas.backend.inventario.Kardex;
import com.jventas.backend.inventario.KardexRepository;
import com.jventas.backend.inventario.TipoDocumentoKardex;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.producto.ProductoRepository;
import com.jventas.backend.serieproducto.SerieProductoService;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dos pasos, a propósito: crear() saca el stock del origen de inmediato
 * (ya no está disponible ahí, está "en tránsito"); completar() recién lo
 * suma al destino cuando alguien confirma que llegó. Un traslado pendiente
 * se puede anular (el stock vuelve al origen); uno ya completado no — eso
 * necesitaría un traslado nuevo en sentido contrario.
 */
@Service
@RequiredArgsConstructor
public class TrasladoService {

    private final TrasladoAlmacenRepository trasladoRepository;
    private final DetalleTrasladoRepository detalleTrasladoRepository;
    private final AlmacenRepository almacenRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final AlmacenStockRepository almacenStockRepository;
    private final KardexRepository kardexRepository;
    private final AccesoAlmacenService accesoAlmacenService;
    private final SerieProductoService serieProductoService;

    @Transactional(readOnly = true)
    public Page<TrasladoResumenResponse> listar(Pageable pageable) {
        return trasladoRepository.findByActivoTrue(pageable).map(TrasladoResumenResponse::from);
    }

    @Transactional(readOnly = true)
    public TrasladoDetalleResponse obtener(Long id) {
        TrasladoAlmacen traslado = obtenerEntidad(id);
        return TrasladoDetalleResponse.from(traslado, detalleTrasladoRepository.findByTrasladoId(id));
    }

    @Transactional
    public TrasladoDetalleResponse crear(TrasladoRequest request, String loginUsuario) {
        if (request.almacenOrigenId().equals(request.almacenDestinoId())) {
            throw new IllegalArgumentException("El almacén de origen y destino no pueden ser el mismo");
        }

        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, request.almacenOrigenId());

        TrasladoAlmacen traslado = new TrasladoAlmacen();
        traslado.setAlmacenOrigen(referenciaAlmacen(request.almacenOrigenId()));
        traslado.setAlmacenDestino(referenciaAlmacen(request.almacenDestinoId()));
        traslado.setUsuario(usuario);
        traslado.setObservaciones(request.observaciones());
        traslado = trasladoRepository.save(traslado);

        for (DetalleTrasladoRequest detalleRequest : request.detalles()) {
            Producto producto = productoRepository
                    .findById(detalleRequest.productoId())
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + detalleRequest.productoId()));

            DetalleTraslado detalle = new DetalleTraslado();
            detalle.setTraslado(traslado);
            detalle.setProducto(producto);
            detalle.setCantidad(detalleRequest.cantidad());
            detalleTrasladoRepository.save(detalle);

            int nuevoStockOrigen = decrementarStockValidando(traslado.getAlmacenOrigen(), producto, detalleRequest.cantidad());

            Kardex kardex = new Kardex();
            kardex.setAlmacen(traslado.getAlmacenOrigen());
            kardex.setProducto(producto);
            kardex.setTipoDocumento(TipoDocumentoKardex.TRASLADO_SALIDA);
            kardex.setUsuario(usuario);
            kardex.setTraslado(traslado);
            kardex.setSalida(detalleRequest.cantidad());
            // trasladar no cambia el promedio -- el costo viaja con el traslado tal cual estaba
            kardex.setCostoUnitario(producto.getCosto());
            kardex.setCostoTotal(producto.getCosto().multiply(BigDecimal.valueOf(detalleRequest.cantidad())));
            kardex.setStockResultante(nuevoStockOrigen);
            kardexRepository.save(kardex);
        }

        return TrasladoDetalleResponse.from(traslado, detalleTrasladoRepository.findByTrasladoId(traslado.getId()));
    }

    @Transactional
    public TrasladoDetalleResponse completar(Long id, String loginUsuario) {
        TrasladoAlmacen traslado = obtenerEntidad(id);
        if (traslado.getEstado() != EstadoTraslado.PENDIENTE) {
            throw new IllegalArgumentException("Solo se puede completar un traslado pendiente (estado actual: " + traslado.getEstado() + ")");
        }
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, traslado.getAlmacenDestino().getId());

        for (DetalleTraslado detalle : detalleTrasladoRepository.findByTrasladoId(id)) {
            if (serieProductoService.requiereSerie(detalle.getProducto())) {
                serieProductoService.moverPorTraslado(
                        detalle.getProducto(), traslado.getAlmacenOrigen(), traslado.getAlmacenDestino(), detalle.getCantidad());
            }

            int nuevoStockDestino = incrementarStock(traslado.getAlmacenDestino(), detalle.getProducto(), detalle.getCantidad());
            BigDecimal costoVigente = detalle.getProducto().getCosto();

            Kardex kardex = new Kardex();
            kardex.setAlmacen(traslado.getAlmacenDestino());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.TRASLADO_ENTRADA);
            kardex.setUsuario(usuario);
            kardex.setTraslado(traslado);
            kardex.setEntrada(detalle.getCantidad());
            kardex.setCostoUnitario(costoVigente);
            kardex.setCostoTotal(costoVigente.multiply(BigDecimal.valueOf(detalle.getCantidad())));
            kardex.setStockResultante(nuevoStockDestino);
            kardexRepository.save(kardex);
        }

        traslado.setEstado(EstadoTraslado.COMPLETADO);
        traslado = trasladoRepository.save(traslado);
        return TrasladoDetalleResponse.from(traslado, detalleTrasladoRepository.findByTrasladoId(id));
    }

    @Transactional
    public TrasladoDetalleResponse anular(Long id, String loginUsuario) {
        TrasladoAlmacen traslado = obtenerEntidad(id);
        if (traslado.getEstado() != EstadoTraslado.PENDIENTE) {
            throw new IllegalArgumentException(
                    "Solo se puede anular un traslado pendiente (estado actual: " + traslado.getEstado() + ")");
        }
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));
        accesoAlmacenService.validarAcceso(loginUsuario, traslado.getAlmacenOrigen().getId());

        for (DetalleTraslado detalle : detalleTrasladoRepository.findByTrasladoId(id)) {
            // el stock nunca llegó a salir del sistema, solo estaba "en tránsito" — vuelve al origen
            int nuevoStockOrigen = incrementarStock(traslado.getAlmacenOrigen(), detalle.getProducto(), detalle.getCantidad());
            BigDecimal costoVigente = detalle.getProducto().getCosto();

            Kardex kardex = new Kardex();
            kardex.setAlmacen(traslado.getAlmacenOrigen());
            kardex.setProducto(detalle.getProducto());
            kardex.setTipoDocumento(TipoDocumentoKardex.TRASLADO_ENTRADA);
            kardex.setUsuario(usuario);
            kardex.setTraslado(traslado);
            kardex.setEntrada(detalle.getCantidad());
            kardex.setCostoUnitario(costoVigente);
            kardex.setCostoTotal(costoVigente.multiply(BigDecimal.valueOf(detalle.getCantidad())));
            kardex.setStockResultante(nuevoStockOrigen);
            kardexRepository.save(kardex);
        }

        traslado.setEstado(EstadoTraslado.ANULADO);
        traslado = trasladoRepository.save(traslado);
        return TrasladoDetalleResponse.from(traslado, detalleTrasladoRepository.findByTrasladoId(id));
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

    private Almacen referenciaAlmacen(Long id) {
        return almacenRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Almacén no encontrado: " + id));
    }

    private TrasladoAlmacen obtenerEntidad(Long id) {
        return trasladoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Traslado no encontrado: " + id));
    }
}
