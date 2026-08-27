package com.jventas.backend.usuario;

import java.util.List;

public record RolResponse(Long id, String nombre, String descripcion, List<PermisoResponse> permisos) {

    public static RolResponse from(Rol rol) {
        List<PermisoResponse> permisos = rol.getPermisos().stream()
                .sorted((a, b) -> a.getCodigo().compareTo(b.getCodigo()))
                .map(PermisoResponse::from)
                .toList();
        return new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), permisos);
    }
}
