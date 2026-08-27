package com.jventas.backend.serieproducto;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SerieProductoRepository extends JpaRepository<SerieProducto, Long> {

    /**
     * Series disponibles para elegir al vender -- en este almacén, activas y
     * sin vender todavía. join fetch en producto/almacén: el controller que
     * la usa no es transaccional, y SerieProductoResponse los lee para
     * armar la respuesta -- sin el fetch, revienta con
     * LazyInitializationException (la sesión ya cerró) en vez de N+1.
     */
    @Query("select s from SerieProducto s join fetch s.producto join fetch s.almacen"
            + " where s.producto.id = :productoId and s.almacen.id = :almacenId"
            + " and s.activo = true and s.vendido = false order by s.numeroSerie asc")
    List<SerieProducto> findByProductoIdAndAlmacenIdAndActivoTrueAndVendidoFalseOrderByNumeroSerieAsc(
            @Param("productoId") Long productoId, @Param("almacenId") Long almacenId);

    /**
     * Igual que la anterior, pero bloqueando las filas -- la usa
     * SerieProductoService.moverPorTraslado() para elegir qué series mover.
     * Sin el lock, dos traslados concurrentes del mismo producto desde el
     * mismo almacén podrían elegir las mismas series (mismo tipo de
     * problema que el resto de los locks de esta sesión).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerieProducto s where s.producto.id = :productoId and s.almacen.id = :almacenId"
            + " and s.activo = true and s.vendido = false order by s.numeroSerie asc")
    List<SerieProducto> findDisponiblesParaActualizar(@Param("productoId") Long productoId, @Param("almacenId") Long almacenId);

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de marcarla vendida --
     * mismo motivo que los demás locks de esta sesión: sin esto, dos ventas
     * concurrentes podrían elegir la misma serie y una de las dos
     * asignaciones se pierde (la última en confirmar "gana" la referencia
     * a detalle_venta, silenciosamente).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerieProducto s where s.producto.id = :productoId and s.numeroSerie = :numeroSerie and s.activo = true")
    Optional<SerieProducto> findParaVender(@Param("productoId") Long productoId, @Param("numeroSerie") String numeroSerie);

    List<SerieProducto> findByDetalleVentaId(Long detalleVentaId);

    List<SerieProducto> findByDetalleCompraId(Long detalleCompraId);
}
