package com.jventas.backend.producto;

import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductoPrecioId implements Serializable {

    private Long producto;
    private Long listaPrecio;

    public ProductoPrecioId(Long producto, Long listaPrecio) {
        this.producto = producto;
        this.listaPrecio = listaPrecio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductoPrecioId that)) {
            return false;
        }
        return Objects.equals(producto, that.producto) && Objects.equals(listaPrecio, that.listaPrecio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producto, listaPrecio);
    }
}
