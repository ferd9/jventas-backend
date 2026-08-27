package com.jventas.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {

    List<ListaPrecio> findByActivoTrueOrderByNombreAsc();
}
