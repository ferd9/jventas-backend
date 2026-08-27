package com.jventas.backend.direccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** No se elige de una lista como los catálogos — cada almacén/proveedor/cliente tiene la suya propia. */
@Entity
@Table(name = "direccion")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pais = "Perú";

    private String departamento;

    private String provincia;

    private String distrito;

    @Column(name = "direccion_linea", nullable = false)
    private String direccionLinea;

    private String referencia;
}
