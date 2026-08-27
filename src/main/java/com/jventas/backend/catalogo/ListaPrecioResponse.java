package com.jventas.backend.catalogo;

public record ListaPrecioResponse(Long id, String nombre) {

    public static ListaPrecioResponse from(ListaPrecio listaPrecio) {
        return new ListaPrecioResponse(listaPrecio.getId(), listaPrecio.getNombre());
    }
}
