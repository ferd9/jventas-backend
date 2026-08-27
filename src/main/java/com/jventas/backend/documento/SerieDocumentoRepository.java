package com.jventas.backend.documento;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SerieDocumentoRepository extends JpaRepository<SerieDocumento, Long> {

    List<SerieDocumento> findByActivoTrueAndAlmacenIdAndTipoDocumentoId(Long almacenId, Long tipoDocumentoId);

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de incrementar el
     * correlativo — sin esto, dos compras concurrentes con la misma serie
     * podrían leer el mismo número y emitir un duplicado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerieDocumento s where s.id = :id")
    Optional<SerieDocumento> findByIdParaActualizar(Long id);
}
