package com.jventas.backend.almacen;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlmacenRepository extends JpaRepository<Almacen, Long> {

    List<Almacen> findByActivoTrueOrderByNombreAsc();
}
