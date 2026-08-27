package com.jventas.backend.devolucion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    /** join fetch en usuario -- lo lee DevolucionResponse para armar el listado. */
    @Query("select d from Devolucion d join fetch d.usuario where d.venta.id = :ventaId and d.activo = true order by d.fecha desc")
    List<Devolucion> findByVentaIdOrderByFechaDesc(Long ventaId);
}
