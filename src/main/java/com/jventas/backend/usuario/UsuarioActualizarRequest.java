package com.jventas.backend.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Sin password a propósito — eso pasa por /me/password (propio) o /resetear-password (admin), nunca por acá. */
public record UsuarioActualizarRequest(
        @NotBlank String dni,
        @NotBlank String codigo,
        @NotBlank String login,
        @NotBlank String nombre,
        @NotBlank String apellidos,
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
