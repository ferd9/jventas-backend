package com.jventas.backend.inventario;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.compra.Compra;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.traslado.TrasladoAlmacen;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.venta.Venta;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Historial de movimientos de inventario — el rastro completo de compra, venta y traslado. */
@Entity
@Table(name = "kardex")
@Getter
@Setter
@NoArgsConstructor
public class Kardex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "almacen_id", nullable = false)
    private Almacen almacen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private LocalDate fecha = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumentoKardex tipoDocumento;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traslado_id")
    private TrasladoAlmacen traslado;

    private int entrada;

    private int salida;

    private BigDecimal precio;

    private BigDecimal valor;

    /**
     * Costeo por promedio ponderado. costoUnitario es el costo de ESTE
     * movimiento puntual -- en COMPRA, lo que se pagó (coincide con
     * `precio`); en VENTA/TRASLADO/APERTURA, el `producto.costo` vigente al
     * momento del movimiento (vender/trasladar no cambian el promedio,
     * solo comprar; la apertura usa el costo ya declarado en el producto
     * como base, al no tener un precio de compra propio).
     */
    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    @Column(name = "costo_total")
    private BigDecimal costoTotal;

    @Column(name = "stock_resultante", nullable = false)
    private int stockResultante;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    private boolean activo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
