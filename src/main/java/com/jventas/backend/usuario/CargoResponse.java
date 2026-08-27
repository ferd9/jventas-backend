package com.jventas.backend.usuario;

public record CargoResponse(Long id, String nombre, boolean activo) {

    public static CargoResponse from(Cargo cargo) {
        return new CargoResponse(cargo.getId(), cargo.getNombre(), cargo.isActivo());
    }
}
