package com.jventas.backend.compra;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, Long> {

    List<DetalleCompra> findByCompraId(Long compraId);

    /**
     * Bloquea la fila (SELECT ... FOR UPDATE) antes de validar cuánto se
     * puede devolver al proveedor -- mismo motivo que
     * DetalleVentaRepository.findByIdParaActualizar().
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DetalleCompra d where d.id = :id")
    Optional<DetalleCompra> findByIdParaActualizar(Long id);
}
