package com.jventas.backend.direccion;

public record DireccionResponse(
        Long id, String pais, String departamento, String provincia, String distrito, String direccionLinea, String referencia) {

    public static DireccionResponse from(Direccion d) {
        return new DireccionResponse(
                d.getId(), d.getPais(), d.getDepartamento(), d.getProvincia(), d.getDistrito(), d.getDireccionLinea(), d.getReferencia());
    }
}
