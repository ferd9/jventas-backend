package com.jventas.backend.almacen;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncargadoAlmacenRepository extends JpaRepository<EncargadoAlmacen, Long> {

    List<EncargadoAlmacen> findByActivoTrueAndUsuarioId(Long usuarioId);

    List<EncargadoAlmacen> findByActivoTrueAndAlmacenId(Long almacenId);

    Optional<EncargadoAlmacen> findByUsuarioIdAndAlmacenId(Long usuarioId, Long almacenId);
}
