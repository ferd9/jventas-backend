package com.jventas.backend.usuario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UsuarioResponse(
        Long id,
        String dni,
        String codigo,
        String login,
        String nombre,
        String apellidos,
        String fotoUrl,
        LocalDate fechaNacimiento,
        String telefono,
        String telefono2,
        String celular,
        String email,
        SexoPersona sexo,
        Long cargoId,
        String cargoNombre,
        String descripcion,
        boolean activo,
        Instant fechaRegistro,
        List<String> roles) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getDni(),
                u.getCodigo(),
                u.getLogin(),
                u.getNombre(),
                u.getApellidos(),
                u.getFotoUrl(),
                u.getFechaNacimiento(),
                u.getTelefono(),
                u.getTelefono2(),
                u.getCelular(),
                u.getEmail(),
                u.getSexo(),
                u.getCargo().getId(),
                u.getCargo().getNombre(),
                u.getDescripcion(),
                u.isActivo(),
                u.getFechaRegistro(),
                u.getRoles().stream().map(Rol::getNombre).sorted().toList());
    }
}
