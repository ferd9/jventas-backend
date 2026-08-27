package com.jventas.backend.almacen;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Asignar el encargado de un almacén es parte de administrarlo -- mismo permiso que crear/editar almacenes. */
@RestController
@RequestMapping("/api/encargados-almacen")
@RequiredArgsConstructor
public class EncargadoAlmacenController {

    private final EncargadoAlmacenService encargadoAlmacenService;

    @GetMapping
    @PreAuthorize("hasAuthority('almacen:ver')")
    public List<EncargadoAlmacenResponse> listar(@RequestParam(required = false) Long almacenId, @RequestParam(required = false) Long usuarioId) {
        if (almacenId != null) {
            return encargadoAlmacenService.porAlmacen(almacenId);
        }
        if (usuarioId != null) {
            return encargadoAlmacenService.porUsuario(usuarioId);
        }
        throw new IllegalArgumentException("Debe indicar almacenId o usuarioId");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('almacen:editar')")
    public ResponseEntity<EncargadoAlmacenResponse> asignar(@Valid @RequestBody EncargadoAlmacenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encargadoAlmacenService.asignar(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('almacen:editar')")
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        encargadoAlmacenService.quitar(id);
        return ResponseEntity.noContent().build();
    }
}
