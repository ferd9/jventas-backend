package com.jventas.backend.devolucion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DevolucionCompraRepository extends JpaRepository<DevolucionCompra, Long> {

    /** join fetch en usuario -- lo lee DevolucionCompraResponse para armar el listado. */
    @Query("select d from DevolucionCompra d join fetch d.usuario where d.compra.id = :compraId and d.activo = true order by d.fecha desc")
    List<DevolucionCompra> findByCompraIdOrderByFechaDesc(Long compraId);
}
