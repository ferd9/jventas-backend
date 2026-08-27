package com.jventas.backend.catalogo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpuestoRepository extends JpaRepository<Impuesto, Long> {

    List<Impuesto> findByActivoTrueOrderByNombreAsc();

    Optional<Impuesto> findByEsDefaultTrue();
}
