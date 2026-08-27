package com.jventas.backend.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("select c from Cliente c where c.activo = true "
            + "and (lower(c.nombre) like lower(concat('%', :q, '%')) or lower(c.apellidos) like lower(concat('%', :q, '%')))")
    Page<Cliente> buscarPorNombreOApellidos(String q, Pageable pageable);

    Page<Cliente> findByActivoTrue(Pageable pageable);

    boolean existsByRuc(String ruc);

    boolean existsByDni(String dni);
}
