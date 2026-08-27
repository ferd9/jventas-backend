package com.jventas.backend.compra;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    /**
     * proveedorId null = sin filtrar por proveedor. fechaDesde/fechaHasta
     * SIEMPRE vienen con un valor real (CompraService reemplaza null por
     * un rango centinela bien amplio) -- nunca comparar contra un
     * parámetro de fecha null. Ver el javadoc de VentaRepository.buscar()
     * para los dos bugs reales (uno de Postgres, otro de Hibernate) que
     * llevaron a este diseño.
     */
    @Query(
            value = """
                    select c from Compra c join fetch c.proveedor
                    where c.activo = true
                      and (:proveedorId is null or c.proveedor.id = :proveedorId)
                      and c.fecha >= :fechaDesde
                      and c.fecha <= :fechaHasta
                    """,
            // el join fetch no es válido en una query de conteo -- sin esta
            // versión aparte, Page.getTotalElements() reventaba
            countQuery = """
                    select count(c) from Compra c
                    where c.activo = true
                      and (:proveedorId is null or c.proveedor.id = :proveedorId)
                      and c.fecha >= :fechaDesde
                      and c.fecha <= :fechaHasta
                    """)
    Page<Compra> buscar(
            @Param("proveedorId") Long proveedorId,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            Pageable pageable);

    /**
     * El estado se pasa como parámetro SpEL (bindeado), no como literal JPQL
     * -- un literal de enum ahí hace que Hibernate genere mal el nombre del
     * tipo nativo de Postgres ("estadotransaccion" en vez de
     * "estado_transaccion") y la query revienta.
     */
    @Query("select c from Compra c join fetch c.proveedor where c.activo = true"
            + " and c.estado = :#{T(com.jventas.backend.compra.EstadoTransaccion).PENDIENTE}"
            + " order by c.fechaVencimiento asc nulls last, c.fecha asc")
    List<Compra> findPendientesConProveedor();
}
