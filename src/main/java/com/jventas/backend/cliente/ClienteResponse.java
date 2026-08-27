package com.jventas.backend.cliente;

import com.jventas.backend.direccion.DireccionResponse;
import com.jventas.backend.usuario.SexoPersona;

public record ClienteResponse(
        Long id,
        String ruc,
        String dni,
        String nombre,
        String apellidos,
        TipoCliente tipo,
        DireccionResponse direccion,
        String email,
        String telefono,
        String celular,
        SexoPersona sexo,
        boolean activo) {

    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(),
                c.getRuc(),
                c.getDni(),
                c.getNombre(),
                c.getApellidos(),
                c.getTipo(),
                c.getDireccion() != null ? DireccionResponse.from(c.getDireccion()) : null,
                c.getEmail(),
                c.getTelefono(),
                c.getCelular(),
                c.getSexo(),
                c.isActivo());
    }
}
