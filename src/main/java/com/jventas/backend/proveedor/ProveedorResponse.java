package com.jventas.backend.proveedor;

import com.jventas.backend.direccion.DireccionResponse;

public record ProveedorResponse(
        Long id,
        String ruc,
        String razonSocial,
        DireccionResponse direccion,
        String telefono,
        String telefonoAlternativo,
        String cuentaBancaria,
        String nombreContacto,
        String email,
        String rubro,
        boolean activo) {

    public static ProveedorResponse from(Proveedor p) {
        return new ProveedorResponse(
                p.getId(),
                p.getRuc(),
                p.getRazonSocial(),
                DireccionResponse.from(p.getDireccion()),
                p.getTelefono(),
                p.getTelefonoAlternativo(),
                p.getCuentaBancaria(),
                p.getNombreContacto(),
                p.getEmail(),
                p.getRubro(),
                p.isActivo());
    }
}
