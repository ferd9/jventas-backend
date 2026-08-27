package com.jventas.backend.usuario;

public record PermisoResponse(Long id, String codigo, String descripcion) {

    public static PermisoResponse from(Permiso permiso) {
        return new PermisoResponse(permiso.getId(), permiso.getCodigo(), permiso.getDescripcion());
    }
}
