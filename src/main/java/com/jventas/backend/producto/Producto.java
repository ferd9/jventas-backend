package com.jventas.backend.producto;

import com.jventas.backend.catalogo.Categoria;
import com.jventas.backend.catalogo.Impuesto;
import com.jventas.backend.catalogo.Marca;
import com.jventas.backend.catalogo.Modelo;
import com.jventas.backend.catalogo.UnidadMedida;
import com.jventas.backend.moneda.Moneda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", nullable = false)
    private String codigoBarras;

    @Column(nullable = false)
    private String codigo;

    @Column(name = "codigo_fabricante")
    private String codigoFabricante;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private BigDecimal costo;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoProducto tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impuesto_id")
    private Impuesto impuesto;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id")
    private Modelo modelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_medida_id")
    private UnidadMedida unidadMedida;

    private String ubicacion;

    private BigDecimal peso = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;
}
