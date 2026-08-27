package com.jventas.backend.producto;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de recalcular el costo
     * promedio ponderado -- mismo motivo que el lock de AlmacenStock: dos
     * compras concurrentes del mismo producto (aunque sean en almacenes
     * distintos, el costo es global por producto) pueden leer el mismo
     * costo/stock total y una de las dos actualizaciones se pierde.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdParaActualizarCosto(@Param("id") Long id);

    /**
     * left join fetch (no join fetch a secas) porque categoria/marca son
     * opcionales en la entidad -- un producto sin ninguna de las dos no
     * debe desaparecer del listado. Sin el fetch, ProductoResumenResponse
     * dispara un N+1 real: una query por producto de la página para leer
     * categoriaNombre/marcaNombre.
     */
    @Query(
            value = "select p from Producto p left join fetch p.categoria left join fetch p.marca"
                    + " where p.activo = true and lower(p.nombre) like lower(concat('%', :nombre, '%'))",
            countQuery = "select count(p) from Producto p where p.activo = true and lower(p.nombre) like lower(concat('%', :nombre, '%'))")
    Page<Producto> buscarPorNombre(@Param("nombre") String nombre, Pageable pageable);

    @Query(
            value = "select p from Producto p left join fetch p.categoria left join fetch p.marca where p.activo = true",
            countQuery = "select count(p) from Producto p where p.activo = true")
    Page<Producto> findByActivoTrue(Pageable pageable);

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigo(String codigo);
}
