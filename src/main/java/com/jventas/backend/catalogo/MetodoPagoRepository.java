package com.jventas.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {

    List<MetodoPago> findByActivoTrueOrderByNombreAsc();
}
