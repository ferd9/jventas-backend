package com.jventas.backend.seguridad;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaSesionRepository extends JpaRepository<AuditoriaSesion, Long> {

    List<AuditoriaSesion> findByUsuarioIdOrderByFechaActividadDesc(Long usuarioId);
}
