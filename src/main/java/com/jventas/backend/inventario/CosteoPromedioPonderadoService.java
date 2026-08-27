package com.jventas.backend.inventario;

import com.jventas.backend.producto.Producto;
import com.jventas.backend.producto.ProductoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Costeo por promedio ponderado -- decisión de negocio confirmada (no PEPS
 * ni UEPS). `producto.costo` es el promedio vigente, global entre todos los
 * almacenes (no hay un costo distinto por almacén). Solo una COMPRA cambia
 * el promedio; vender o trasladar no lo tocan, solo lo leen.
 *
 * Nota conocida: revertir_ una compra (anular) deshace el efecto de ESA
 * compra puntual sobre el promedio actual -- es matemáticamente exacto solo
 * si nada más movió el costo entre medio. Si se anula una compra vieja
 * después de que hubo compras más nuevas a otro precio, el promedio
 * resultante puede no ser exactamente el que había antes de la compra que
 * se anula. Es la misma limitación que acepta cualquier costeo por promedio
 * (a diferencia de PEPS, no hay un historial de lotes que permita
 * reconstruirlo con precisión) -- documentado acá para no descubrirlo tarde.
 */
@Service
@RequiredArgsConstructor
public class CosteoPromedioPonderadoService {

    private final ProductoRepository productoRepository;
    private final AlmacenStockRepository almacenStockRepository;

    @Transactional
    public BigDecimal registrarCompra(Long productoId, int cantidadComprada, BigDecimal precioUnitario) {
        Producto producto = productoBloqueado(productoId);
        int stockActual = almacenStockRepository.sumarStockActivoDeProducto(productoId);

        BigDecimal nuevoCosto = promedioPonderado(stockActual, producto.getCosto(), cantidadComprada, precioUnitario);
        producto.setCosto(nuevoCosto);
        productoRepository.save(producto);
        return nuevoCosto;
    }

    /** Deshace el efecto de una compra anulada sobre el promedio -- ver la limitación documentada en la clase. */
    @Transactional
    public void revertirCompra(Long productoId, int cantidadComprada, BigDecimal precioUnitario) {
        Producto producto = productoBloqueado(productoId);
        int stockDespues = almacenStockRepository.sumarStockActivoDeProducto(productoId);
        int stockAntes = stockDespues - cantidadComprada;

        if (stockAntes <= 0) {
            // no queda (o nunca quedó) stock de otras compras -- no hay promedio que reconstruir
            return;
        }

        BigDecimal valorTotalDespues = producto.getCosto().multiply(BigDecimal.valueOf(stockDespues));
        BigDecimal valorEstaCompra = precioUnitario.multiply(BigDecimal.valueOf(cantidadComprada));
        BigDecimal costoAntes = valorTotalDespues
                .subtract(valorEstaCompra)
                .divide(BigDecimal.valueOf(stockAntes), 3, RoundingMode.HALF_UP);

        producto.setCosto(costoAntes);
        productoRepository.save(producto);
    }

    private BigDecimal promedioPonderado(int stockActual, BigDecimal costoActual, int cantidadComprada, BigDecimal precioUnitario) {
        if (stockActual <= 0) {
            // sin stock previo (o negativo por algún ajuste manual) -- el nuevo costo es directamente el de esta compra
            return precioUnitario;
        }
        BigDecimal valorActual = costoActual.multiply(BigDecimal.valueOf(stockActual));
        BigDecimal valorNuevo = precioUnitario.multiply(BigDecimal.valueOf(cantidadComprada));
        int stockTotal = stockActual + cantidadComprada;
        return valorActual.add(valorNuevo).divide(BigDecimal.valueOf(stockTotal), 3, RoundingMode.HALF_UP);
    }

    private Producto productoBloqueado(Long productoId) {
        return productoRepository
                .findByIdParaActualizarCosto(productoId)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + productoId));
    }
}
