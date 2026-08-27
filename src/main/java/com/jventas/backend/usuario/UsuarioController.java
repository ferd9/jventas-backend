package com.jventas.backend.usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioCrearRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioActualizarRequest request) {
        return usuarioService.actualizar(id, request);
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        usuarioService.reactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resetear-password")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public ResetearPasswordResponse resetearPassword(@PathVariable Long id, @RequestBody(required = false) ResetearPasswordRequest request) {
        return usuarioService.resetearPassword(id, request != null ? request : new ResetearPasswordRequest(null));
    }

    /** Self-service: cualquier usuario autenticado puede cambiar su propia contraseña, sin permiso especial. */
    @PostMapping("/me/password")
    public ResponseEntity<Void> cambiarPasswordPropia(@Valid @RequestBody CambiarPasswordRequest request, Authentication authentication) {
        usuarioService.cambiarPasswordPropia(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
