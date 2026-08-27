package com.jventas.backend.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String dni;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String login;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellidos;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(nullable = false)
    private String telefono;

    private String telefono2;

    private String celular;

    private String email;

    // El enum de Java se mapea al tipo nativo `sexo_persona` de Postgres, no a
    // un varchar — sin JdbcTypeCode(NAMED_ENUM), Postgres rechaza el INSERT
    // ("column is of type sexo_persona but expression is of type character
    // varying"). Validado end-to-end contra Postgres real, ver notas del README.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SexoPersona sexo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;

    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // @BatchSize en vez de join fetch en el repositorio -- una colección
    // fetch-joineada junto con LIMIT/OFFSET hace que Hibernate pagine en
    // memoria. Esto en cambio agrupa la carga lazy de "roles" de varios
    // usuarios de la misma página en un solo "in (...)", sin ese problema.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    @BatchSize(size = 25)
    private Set<Rol> roles = new HashSet<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (fechaRegistro == null) {
            fechaRegistro = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
