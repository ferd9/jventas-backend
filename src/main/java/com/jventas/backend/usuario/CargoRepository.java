package com.jventas.backend.usuario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

    Optional<Cargo> findByNombre(String nombre);

    List<Cargo> findByActivoTrueOrderByNombreAsc();
}
