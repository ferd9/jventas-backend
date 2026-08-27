package com.jventas.backend.almacen;

import com.jventas.backend.direccion.DireccionResponse;

public record AlmacenResponse(Long id, String nombre, DireccionResponse direccion, boolean activo) {

    public static AlmacenResponse from(Almacen almacen) {
        return new AlmacenResponse(
                almacen.getId(), almacen.getNombre(), DireccionResponse.from(almacen.getDireccion()), almacen.isActivo());
    }
}
