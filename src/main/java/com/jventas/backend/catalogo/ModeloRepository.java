package com.jventas.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloRepository extends JpaRepository<Modelo, Long> {

    List<Modelo> findByActivoTrueOrderByNombreAsc();

    List<Modelo> findByMarcaIdAndActivoTrueOrderByNombreAsc(Long marcaId);
}
