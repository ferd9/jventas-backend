package com.jventas.backend.producto;

import java.math.BigDecimal;

public record PrecioResponse(Long listaPrecioId, String listaPrecioNombre, BigDecimal precio) {

    public static PrecioResponse from(ProductoPrecio productoPrecio) {
        return new PrecioResponse(
                productoPrecio.getListaPrecio().getId(),
                productoPrecio.getListaPrecio().getNombre(),
                productoPrecio.getPrecio());
    }
}
