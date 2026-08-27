package com.jventas.backend.producto;

import com.jventas.backend.catalogo.ListaPrecio;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de producto_precio: el precio de un producto en una lista puntual (Mayorista, Minorista...). */
@Entity
@Table(name = "producto_precio")
@Getter
@Setter
@NoArgsConstructor
public class ProductoPrecio {

    @EmbeddedId
    private ProductoPrecioId id = new ProductoPrecioId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("producto")
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("listaPrecio")
    @JoinColumn(name = "lista_precio_id")
    private ListaPrecio listaPrecio;

    private BigDecimal precio;

    public ProductoPrecio(Producto producto, ListaPrecio listaPrecio, BigDecimal precio) {
        this.producto = producto;
        this.listaPrecio = listaPrecio;
        this.precio = precio;
        this.id = new ProductoPrecioId(producto.getId(), listaPrecio.getId());
    }
}
