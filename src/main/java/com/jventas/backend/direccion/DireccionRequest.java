package com.jventas.backend.direccion;

import jakarta.validation.constraints.NotBlank;

public record DireccionRequest(
        String pais, String departamento, String provincia, String distrito, @NotBlank String direccionLinea, String referencia) {

    public Direccion toEntity() {
        Direccion direccion = new Direccion();
        aplicarA(direccion);
        return direccion;
    }

    public void aplicarA(Direccion direccion) {
        if (pais != null && !pais.isBlank()) {
            direccion.setPais(pais);
        }
        direccion.setDepartamento(departamento);
        direccion.setProvincia(provincia);
        direccion.setDistrito(distrito);
        direccion.setDireccionLinea(direccionLinea);
        direccion.setReferencia(referencia);
    }
}
