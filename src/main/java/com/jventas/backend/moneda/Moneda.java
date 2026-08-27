package com.jventas.backend.moneda;

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

@Entity
@Table(name = "moneda")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Moneda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, length = 3)
    private String simbolo;

    @Column(name = "codigo_iso", length = 3)
    private String codigoIso;

    @Column(name = "es_predeterminada", nullable = false)
    private boolean predeterminada;

    @Column(nullable = false)
    private boolean activo = true;
}
