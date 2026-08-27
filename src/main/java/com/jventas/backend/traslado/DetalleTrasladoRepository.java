package com.jventas.backend.traslado;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleTrasladoRepository extends JpaRepository<DetalleTraslado, Long> {

    List<DetalleTraslado> findByTrasladoId(Long trasladoId);
}
