package com.jventas.backend.usuario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombre(String nombre);

    // join fetch -- sin esto, RolResponse.from() revienta con LazyInitializationException
    // al leer rol.getPermisos() fuera de la sesión, y con N+1 si se hace por cada rol.
    @Query("select distinct r from Rol r left join fetch r.permisos order by r.nombre")
    List<Rol> findAllConPermisos();

    @Query("select r from Rol r left join fetch r.permisos where r.id = :id")
    Optional<Rol> findByIdConPermisos(Long id);
}
