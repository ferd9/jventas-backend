package com.jventas.backend.inventario;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AlmacenStockRepository extends JpaRepository<AlmacenStock, Long> {

    Optional<AlmacenStock> findByAlmacenIdAndProductoId(Long almacenId, Long productoId);

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de sumar/restar --
     * mismo patrón que SerieDocumentoRepository.findByIdParaActualizar().
     * Sin esto, dos ventas concurrentes del mismo producto en el mismo
     * almacén pueden leer el mismo cantidadActual y una de las dos
     * actualizaciones se pierde (lost update) -- el CHECK
     * cantidad_actual >= 0 no lo evita, porque cada UPDATE valida contra
     * su propia lectura, no contra la del otro. No aplica a la creación
     * de una fila nueva (no hay fila que bloquear todavía); ese caso ya
     * está cubierto por el UNIQUE(almacen_id, producto_id) del esquema.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AlmacenStock s where s.almacen.id = :almacenId and s.producto.id = :productoId")
    Optional<AlmacenStock> findByAlmacenIdAndProductoIdParaActualizar(Long almacenId, Long productoId);

    List<AlmacenStock> findByActivoTrueAndAlmacenId(Long almacenId);

    List<AlmacenStock> findByActivoTrueAndProductoId(Long productoId);

    @Query("select s from AlmacenStock s where s.activo = true and s.cantidadActual < s.producto.stockMinimo")
    List<AlmacenStock> findConStockBajoElMinimo();

    /** Stock total del producto sumando todos los almacenes -- lo que necesita el costeo promedio ponderado, que es global por producto, no por almacén. */
    @Query("select coalesce(sum(s.cantidadActual), 0) from AlmacenStock s where s.activo = true and s.producto.id = :productoId")
    int sumarStockActivoDeProducto(Long productoId);
}
