package com.jventas.backend.inventario;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KardexRepository extends JpaRepository<Kardex, Long> {

    // paginado -- un producto de alta rotación con meses de historial puede tener miles de filas
    Page<Kardex> findByAlmacenIdAndProductoIdOrderByFechaAscIdAsc(Long almacenId, Long productoId, Pageable pageable);

    List<Kardex> findByCompraIdOrderByIdAsc(Long compraId);
}
