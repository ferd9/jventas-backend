package com.jventas.backend.catalogo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoDocumentoRepository extends JpaRepository<TipoDocumento, Long> {

    List<TipoDocumento> findByActivoTrueAndAplicaCompraTrueOrderByNombreAsc();

    List<TipoDocumento> findByActivoTrueAndAplicaVentaTrueOrderByNombreAsc();
}
