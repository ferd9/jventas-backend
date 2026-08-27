package com.jventas.backend.serieproducto;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.cliente.Cliente;
import com.jventas.backend.compra.DetalleCompra;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.proveedor.Proveedor;
import com.jventas.backend.venta.DetalleVenta;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisión de negocio confirmada: la trazabilidad por serie aplica a los
 * productos cuya categoría lo exige (`categoria.requiere_serie`), se
 * captura el número de serie de cada unidad al comprar, y el vendedor
 * elige manualmente cuál sale al vender (sin asignación automática).
 *
 * Traslado es la excepción deliberada -- mover series entre almacenes no
 * pasa por selección manual, se reasignan automáticamente al completar
 * (ver moverPorTraslado): la decisión de negocio fue específicamente
 * sobre la venta a un cliente, no sobre un movimiento interno de stock.
 */
@Service
@RequiredArgsConstructor
public class SerieProductoService {

    private final SerieProductoRepository serieProductoRepository;

    public boolean requiereSerie(Producto producto) {
        return producto.getCategoria() != null && producto.getCategoria().isRequiereSerie();
    }

    @Transactional
    public void registrarPorCompra(DetalleCompra detalle, List<String> numerosSerie, Almacen almacen, Proveedor proveedor) {
        for (String numeroSerie : numerosSerie) {
            SerieProducto serie = new SerieProducto();
            serie.setProducto(detalle.getProducto());
            serie.setNumeroSerie(numeroSerie);
            serie.setAlmacen(almacen);
            serie.setProveedor(proveedor);
            serie.setDetalleCompra(detalle);
            serieProductoRepository.save(serie);
        }
    }

    /** Al anular una compra, las series que trajo nunca debieron existir -- se desactivan, no se borran (mismo criterio que el resto del kardex). */
    @Transactional
    public void revertirPorAnularCompra(DetalleCompra detalle) {
        for (SerieProducto serie : serieProductoRepository.findByDetalleCompraId(detalle.getId())) {
            serie.setActivo(false);
            serieProductoRepository.save(serie);
        }
    }

    /** Valida cada serie (existe, activa, en el almacén correcto, sin vender todavía) y la marca vendida. */
    @Transactional
    public void venderSeries(DetalleVenta detalle, List<String> numerosSerie, Long almacenId, Cliente cliente) {
        for (String numeroSerie : numerosSerie) {
            SerieProducto serie = serieProductoRepository
                    .findParaVender(detalle.getProducto().getId(), numeroSerie)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la serie " + numeroSerie + " para " + detalle.getProducto().getNombre()));

            if (serie.isVendido()) {
                throw new IllegalArgumentException("La serie " + numeroSerie + " ya fue vendida");
            }
            if (serie.getAlmacen() == null || !serie.getAlmacen().getId().equals(almacenId)) {
                throw new IllegalArgumentException("La serie " + numeroSerie + " no está en el almacén de esta venta");
            }

            serie.setVendido(true);
            serie.setDetalleVenta(detalle);
            serie.setCliente(cliente);
            serieProductoRepository.save(serie);
        }
    }

    /** Al anular una venta, las series vendidas vuelven a estar disponibles en el mismo almacén. */
    @Transactional
    public void revertirPorAnularVenta(DetalleVenta detalle) {
        for (SerieProducto serie : serieProductoRepository.findByDetalleVentaId(detalle.getId())) {
            serie.setVendido(false);
            serie.setDetalleVenta(null);
            serie.setCliente(null);
            serieProductoRepository.save(serie);
        }
    }

    /**
     * Devolución parcial: libera exactamente las series indicadas de esta
     * línea de venta (deben existir, estar vendidas, y pertenecer a este
     * detalle_venta puntual -- no basta con que el producto coincida, evita
     * liberar por error la serie de otra venta del mismo producto).
     */
    @Transactional
    public void revertirParcialPorDevolucion(DetalleVenta detalle, List<String> numerosSerie) {
        for (String numeroSerie : numerosSerie) {
            SerieProducto serie = serieProductoRepository
                    .findParaVender(detalle.getProducto().getId(), numeroSerie)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la serie " + numeroSerie + " para " + detalle.getProducto().getNombre()));

            if (!serie.isVendido() || serie.getDetalleVenta() == null || !serie.getDetalleVenta().getId().equals(detalle.getId())) {
                throw new IllegalArgumentException("La serie " + numeroSerie + " no corresponde a esta línea de venta");
            }

            serie.setVendido(false);
            serie.setDetalleVenta(null);
            serie.setCliente(null);
            serieProductoRepository.save(serie);
        }
    }

    /**
     * Devolución de compra al proveedor: desactiva exactamente las series
     * indicadas (deben existir, seguir activas, no estar vendidas -- no se
     * puede devolver al proveedor algo que ya se le vendió a un cliente -- y
     * pertenecer a este detalle_compra puntual). No se borran, mismo
     * criterio que revertirPorAnularCompra().
     */
    @Transactional
    public void revertirParcialPorDevolucionCompra(DetalleCompra detalle, List<String> numerosSerie) {
        for (String numeroSerie : numerosSerie) {
            SerieProducto serie = serieProductoRepository
                    .findParaVender(detalle.getProducto().getId(), numeroSerie)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la serie " + numeroSerie + " para " + detalle.getProducto().getNombre()));

            if (serie.isVendido()) {
                throw new IllegalArgumentException("La serie " + numeroSerie + " ya se vendió a un cliente, no se puede devolver al proveedor");
            }
            if (serie.getDetalleCompra() == null || !serie.getDetalleCompra().getId().equals(detalle.getId())) {
                throw new IllegalArgumentException("La serie " + numeroSerie + " no corresponde a esta línea de compra");
            }

            serie.setActivo(false);
            serieProductoRepository.save(serie);
        }
    }

    /**
     * Reasigna `cantidad` series de origen a destino -- sin selección manual
     * a propósito (ver el javadoc de la clase). Orden determinístico
     * (numeroSerie asc) para que dos traslados del mismo producto no
     * compitan por las mismas unidades de forma arbitraria.
     */
    @Transactional
    public void moverPorTraslado(Producto producto, Almacen origen, Almacen destino, int cantidad) {
        List<SerieProducto> disponibles = serieProductoRepository.findDisponiblesParaActualizar(producto.getId(), origen.getId());

        if (disponibles.size() < cantidad) {
            throw new IllegalStateException("Solo hay " + disponibles.size() + " serie(s) de " + producto.getNombre()
                    + " en " + origen.getNombre() + ", se necesitan " + cantidad + " para el traslado");
        }

        for (SerieProducto serie : disponibles.subList(0, cantidad)) {
            serie.setAlmacen(destino);
            serieProductoRepository.save(serie);
        }
    }
}
