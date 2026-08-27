package com.jventas.backend.devolucion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DetalleDevolucionRepository extends JpaRepository<DetalleDevolucion, Long> {

    /** join fetch en producto (a través de detalleVenta) -- lo lee DetalleDevolucionResponse. */
    @Query("select dd from DetalleDevolucion dd join fetch dd.detalleVenta dv join fetch dv.producto where dd.devolucion.id = :devolucionId")
    List<DetalleDevolucion> findByDevolucionId(Long devolucionId);

    @Query("select coalesce(sum(dd.cantidad), 0) from DetalleDevolucion dd"
            + " where dd.detalleVenta.id = :detalleVentaId and dd.devolucion.activo = true")
    int sumarCantidadDevuelta(Long detalleVentaId);
}
