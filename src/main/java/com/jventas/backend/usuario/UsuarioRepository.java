package com.jventas.backend.usuario;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLoginAndActivoTrue(String login);

    /**
     * join fetch en cargo (ManyToOne, compatible con paginación) -- sin
     * esto, UsuarioResponse dispara una query extra por usuario de la
     * página al leer cargoNombre. `roles` (ManyToMany) NO se puede
     * fetch-joinear acá: combinar fetch de una colección con LIMIT/OFFSET
     * hace que Hibernate pagine en memoria (trae todo y corta después,
     * silenciosamente). Esa colección se batch-carga en su lugar --
     * @BatchSize(25) en Usuario.roles.
     */
    @Query(
            value = "select u from Usuario u join fetch u.cargo order by u.nombre asc",
            countQuery = "select count(u) from Usuario u")
    Page<Usuario> findAllByOrderByNombreAsc(Pageable pageable);

    boolean existsByDni(String dni);

    boolean existsByCodigo(String codigo);

    boolean existsByLogin(String login);
}
