package com.jventas.backend.usuario;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un permiso puntual, p.ej. "producto:crear". Ver rol_permiso en el esquema. */
@Entity
@Table(name = "permiso")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;

    private String descripcion;
}
