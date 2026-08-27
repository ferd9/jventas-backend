package com.jventas.backend.venta;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    List<DetalleVenta> findByVentaId(Long ventaId);

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de validar cuánto se
     * puede devolver -- mismo motivo que el resto de los locks de esta
     * sesión: sin esto, dos devoluciones concurrentes de la misma línea
     * podrían leer la misma cantidad ya devuelta y aceptar ambas, sumando
     * más de lo que realmente se vendió.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DetalleVenta d where d.id = :id")
    Optional<DetalleVenta> findByIdParaActualizar(Long id);
}
