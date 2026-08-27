package com.jventas.backend.devolucion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DetalleDevolucionCompraRepository extends JpaRepository<DetalleDevolucionCompra, Long> {

    /** join fetch en producto (a través de detalleCompra) -- lo lee DetalleDevolucionCompraResponse. */
    @Query("select dd from DetalleDevolucionCompra dd join fetch dd.detalleCompra dc join fetch dc.producto"
            + " where dd.devolucionCompra.id = :devolucionCompraId")
    List<DetalleDevolucionCompra> findByDevolucionCompraId(Long devolucionCompraId);

    @Query("select coalesce(sum(dd.cantidad), 0) from DetalleDevolucionCompra dd"
            + " where dd.detalleCompra.id = :detalleCompraId and dd.devolucionCompra.activo = true")
    int sumarCantidadDevuelta(Long detalleCompraId);
}
