package com.jventas.backend.traslado;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrasladoAlmacenRepository extends JpaRepository<TrasladoAlmacen, Long> {

    /**
     * join fetch en los dos almacenes -- sin esto, TrasladoResumenResponse
     * dispara dos queries extra por traslado de la página (origen y
     * destino) al leer sus nombres.
     */
    @Query(
            value = "select t from TrasladoAlmacen t join fetch t.almacenOrigen join fetch t.almacenDestino where t.activo = true",
            countQuery = "select count(t) from TrasladoAlmacen t where t.activo = true")
    Page<TrasladoAlmacen> findByActivoTrue(Pageable pageable);
}
