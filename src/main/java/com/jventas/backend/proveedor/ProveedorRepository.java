package com.jventas.backend.proveedor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Page<Proveedor> findByActivoTrueAndRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);

    Page<Proveedor> findByActivoTrue(Pageable pageable);

    boolean existsByRuc(String ruc);
}
