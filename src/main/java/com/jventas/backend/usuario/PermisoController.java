package com.jventas.backend.usuario;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo lectura -- los permisos son código real (@PreAuthorize hardcodeado en
 * cada controller), crear uno nuevo por API no haría cumplir nada. Esto
 * existe para que la UI de edición de roles pueda mostrar el catálogo
 * completo de permisos disponibles como checkboxes.
 */
@RestController
@RequestMapping("/api/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoRepository permisoRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public List<PermisoResponse> listar() {
        return permisoRepository.findAllByOrderByCodigoAsc().stream().map(PermisoResponse::from).toList();
    }
}
