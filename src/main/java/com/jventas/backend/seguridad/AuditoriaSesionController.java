package com.jventas.backend.seguridad;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria-sesion")
@RequiredArgsConstructor
public class AuditoriaSesionController {

    private final AuditoriaSesionRepository auditoriaSesionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public List<AuditoriaSesionResponse> listar(@RequestParam Long usuarioId) {
        return auditoriaSesionRepository.findByUsuarioIdOrderByFechaActividadDesc(usuarioId).stream()
                .map(AuditoriaSesionResponse::from)
                .toList();
    }
}
