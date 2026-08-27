package com.jventas.backend.venta;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    /**
     * clienteId null = sin filtrar por cliente. fechaDesde/fechaHasta
     * SIEMPRE vienen con un valor real (VentaService reemplaza null por
     * un rango centinela bien amplio) -- nunca comparar contra un
     * parámetro de fecha null. Dos bugs reales distintos con ese patrón,
     * en dos intentos:
     * 1. "? is null or columna >= ?" con el parámetro sin cast: Postgres
     *    no podía inferir su tipo ("could not determine data type").
     * 2. "cast(? as date) is null or ...": arreglaba el (1), pero cuando
     *    el valor Java era null de verdad, Hibernate bindeaba el
     *    parámetro como bytea y el CAST reventaba con
     *    "cannot cast type bytea to date" -- se disparaba en el caso más
     *    común de todos (listar sin filtrar por fecha), encontrado por
     *    IdempotencyFlowTests al pedir el listado sin fechaDesde/Hasta.
     * Evitar pasarle null a la comparación de raíz, en el service, es más
     * simple y robusto que seguir peleando con cómo Hibernate tipa un
     * parámetro null dentro de una expresión.
     */
    @Query(
            value = """
                    select v from Venta v join fetch v.cliente
                    where v.activo = true
                      and (:clienteId is null or v.cliente.id = :clienteId)
                      and v.fecha >= :fechaDesde
                      and v.fecha <= :fechaHasta
                    """,
            // el join fetch no es válido en una query de conteo -- sin esta
            // versión aparte, Page.getTotalElements() reventaba
            countQuery = """
                    select count(v) from Venta v
                    where v.activo = true
                      and (:clienteId is null or v.cliente.id = :clienteId)
                      and v.fecha >= :fechaDesde
                      and v.fecha <= :fechaHasta
                    """)
    Page<Venta> buscar(
            @Param("clienteId") Long clienteId,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            Pageable pageable);

    /**
     * El estado se pasa como parámetro SpEL (bindeado), no como literal JPQL
     * -- un literal de enum ahí hace que Hibernate genere mal el nombre del
     * tipo nativo de Postgres ("estadotransaccion" en vez de
     * "estado_transaccion") y la query revienta.
     */
    @Query("select v from Venta v join fetch v.cliente where v.activo = true"
            + " and v.estado = :#{T(com.jventas.backend.compra.EstadoTransaccion).PENDIENTE}"
            + " order by v.fechaVencimiento asc nulls last, v.fecha asc")
    List<Venta> findPendientesConCliente();
}
