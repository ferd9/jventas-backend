package com.jventas.backend.cliente;

import com.jventas.backend.direccion.DireccionRequest;
import com.jventas.backend.usuario.SexoPersona;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClienteRequest(
        String ruc,
        String dni,
        @NotBlank String nombre,
        @NotBlank String apellidos,
        @NotNull TipoCliente tipo,
        @Valid DireccionRequest direccion,
        @Email String email,
        String telefono,
        String celular,
        SexoPersona sexo) {

    public boolean tieneIdentificacion() {
        return (ruc != null && !ruc.isBlank()) || (dni != null && !dni.isBlank());
    }
}
