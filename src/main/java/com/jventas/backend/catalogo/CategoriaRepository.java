package com.jventas.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** left join fetch en categoriaPadre -- sin esto, CategoriaResponse dispara un N+1 leyendo categoriaPadreId de cada fila. */
    @Query("select c from Categoria c left join fetch c.categoriaPadre where c.activo = true order by c.nombre asc")
    List<Categoria> findByActivoTrueOrderByNombreAsc();
}
