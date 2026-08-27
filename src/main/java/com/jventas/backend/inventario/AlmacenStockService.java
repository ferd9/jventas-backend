package com.jventas.backend.inventario;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.almacen.AlmacenRepository;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.producto.ProductoRepository;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlmacenStockService {

    private final AlmacenStockRepository almacenStockRepository;
    private final AlmacenRepository almacenRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final KardexRepository kardexRepository;

    @Transactional(readOnly = true)
    public List<AlmacenStockResponse> porAlmacen(Long almacenId) {
        return almacenStockRepository.findByActivoTrueAndAlmacenId(almacenId).stream().map(AlmacenStockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlmacenStockResponse> porProducto(Long productoId) {
        return almacenStockRepository.findByActivoTrueAndProductoId(productoId).stream().map(AlmacenStockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlmacenStockResponse> conStockBajoElMinimo() {
        return almacenStockRepository.findConStockBajoElMinimo().stream().map(AlmacenStockResponse::from).toList();
    }

    /**
     * Solo para carga inicial: si el almacén ya tiene una fila de stock para
     * el producto (así sea en 0), se rechaza -- a partir de ahí los cambios
     * de stock pasan por compra, venta o traslado, nunca por acá de nuevo.
     */
    @Transactional
    public List<AlmacenStockResponse> registrarApertura(AperturaRequest request, String loginUsuario) {
        Almacen almacen = almacenRepository
                .findById(request.almacenId())
                .orElseThrow(() -> new NoSuchElementException("Almacén no encontrado: " + request.almacenId()));
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));

        for (DetalleAperturaRequest detalle : request.detalles()) {
            if (almacenStockRepository.findByAlmacenIdAndProductoId(request.almacenId(), detalle.productoId()).isPresent()) {
                Producto producto = productoRepository
                        .findById(detalle.productoId())
                        .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + detalle.productoId()));
                throw new IllegalArgumentException("Ya existe stock registrado de " + producto.getNombre() + " en " + almacen.getNombre()
                        + " — la apertura es solo para carga inicial, usa compra/venta/traslado para ajustarlo");
            }
        }

        for (DetalleAperturaRequest detalle : request.detalles()) {
            Producto producto = productoRepository
                    .findById(detalle.productoId())
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + detalle.productoId()));

            AlmacenStock stock = new AlmacenStock();
            stock.setAlmacen(almacen);
            stock.setProducto(producto);
            stock.setCantidadActual(detalle.cantidad());
            almacenStockRepository.save(stock);

            Kardex kardex = new Kardex();
            kardex.setAlmacen(almacen);
            kardex.setProducto(producto);
            kardex.setTipoDocumento(TipoDocumentoKardex.APERTURA);
            kardex.setUsuario(usuario);
            kardex.setEntrada(detalle.cantidad());
            // la apertura no tiene un precio de compra propio -- usa el costo ya
            // declarado en el producto como base para el promedio ponderado
            kardex.setCostoUnitario(producto.getCosto());
            kardex.setCostoTotal(producto.getCosto().multiply(BigDecimal.valueOf(detalle.cantidad())));
            kardex.setStockResultante(detalle.cantidad());
            kardexRepository.save(kardex);
        }

        return porAlmacen(request.almacenId());
    }
}
