package com.jventas.backend.almacen;

import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si un usuario tiene asignaciones explícitas en encargado_almacen, queda
 * restringido a esos almacenes. Si no tiene ninguna, no hay restricción —
 * cubre administradores y cualquier rol que no opera almacenes concretos.
 * Se llama desde CompraService/VentaService/TrasladoService antes de mover
 * stock, no a nivel de endpoint (el mismo usuario puede tener permiso para
 * comprar en general, pero no en cualquier almacén).
 */
@Service
@RequiredArgsConstructor
public class AccesoAlmacenService {

    private final EncargadoAlmacenRepository encargadoAlmacenRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public void validarAcceso(String loginUsuario, Long almacenId) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));

        List<EncargadoAlmacen> asignaciones = encargadoAlmacenRepository.findByActivoTrueAndUsuarioId(usuario.getId());
        if (asignaciones.isEmpty()) {
            return;
        }

        boolean tieneAcceso = asignaciones.stream().anyMatch(e -> e.getAlmacen().getId().equals(almacenId));
        if (!tieneAcceso) {
            throw new AccessDeniedException("No tienes asignado el almacén " + almacenId);
        }
    }
}
