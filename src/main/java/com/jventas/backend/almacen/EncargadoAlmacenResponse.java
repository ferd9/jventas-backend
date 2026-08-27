package com.jventas.backend.almacen;

public record EncargadoAlmacenResponse(
        Long id, Long usuarioId, String usuarioNombre, Long almacenId, String almacenNombre, TipoCargoAlmacen tipoCargo) {

    public static EncargadoAlmacenResponse from(EncargadoAlmacen e) {
        return new EncargadoAlmacenResponse(
                e.getId(),
                e.getUsuario().getId(),
                e.getUsuario().getNombre() + " " + e.getUsuario().getApellidos(),
                e.getAlmacen().getId(),
                e.getAlmacen().getNombre(),
                e.getTipoCargo());
    }
}
