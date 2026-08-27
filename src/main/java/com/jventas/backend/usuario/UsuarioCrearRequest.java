package com.jventas.backend.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record UsuarioCrearRequest(
        @NotBlank String dni,
        @NotBlank String codigo,
        @NotBlank String login,
        @NotBlank String nombre,
        @NotBlank String apellidos,
        @NotBlank @Size(min = 8, message = "debe tener al menos 8 caracteres") String password,
        LocalDate fechaNacimiento,
        @NotBlank String telefono,
        String telefono2,
        String celular,
        @Email String email,
        @NotNull SexoPersona sexo,
        @NotNull Long cargoId,
        String descripcion,
        List<Long> rolIds) {
}
