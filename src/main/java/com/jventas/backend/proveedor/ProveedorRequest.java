package com.jventas.backend.proveedor;

import com.jventas.backend.direccion.DireccionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProveedorRequest(
        @NotBlank String ruc,
        @NotBlank String razonSocial,
        @NotNull @Valid DireccionRequest direccion,
        String telefono,
        String telefonoAlternativo,
        String cuentaBancaria,
        String nombreContacto,
        @Email String email,
        String rubro) {
}
