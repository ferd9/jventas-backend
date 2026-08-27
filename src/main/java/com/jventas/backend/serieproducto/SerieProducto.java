package com.jventas.backend.serieproducto;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.cliente.Cliente;
import com.jventas.backend.compra.DetalleCompra;
import com.jventas.backend.producto.Producto;
import com.jventas.backend.proveedor.Proveedor;
import com.jventas.backend.venta.DetalleVenta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una unidad individual con identidad propia (electrodomésticos, equipos
 * con garantía) de un producto cuya categoría exige número de serie
 * (`categoria.requiere_serie`). `almacen` es dónde está físicamente ahora
 * -- lo mueve un traslado recién al completarse, igual que `almacen_stock`.
 */
@Entity
@Table(name = "serie_producto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SerieProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_serie", nullable = false)
    private String numeroSerie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "almacen_id")
    private Almacen almacen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_compra_id")
    private DetalleCompra detalleCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_venta_id")
    private DetalleVenta detalleVenta;

    private boolean vendido = false;

    private boolean activo = true;
}
