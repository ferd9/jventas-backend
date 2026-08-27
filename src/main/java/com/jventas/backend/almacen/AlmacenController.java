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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/almacenes")
@RequiredArgsConstructor
public class AlmacenController {

    private final AlmacenService almacenService;

    @GetMapping
    @PreAuthorize("hasAuthority('almacen:ver')")
    public List<AlmacenResponse> listar() {
        return almacenService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('almacen:ver')")
    public AlmacenResponse obtener(@PathVariable Long id) {
        return almacenService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('almacen:crear')")
    public ResponseEntity<AlmacenResponse> crear(@Valid @RequestBody AlmacenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(almacenService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('almacen:editar')")
    public AlmacenResponse actualizar(@PathVariable Long id, @Valid @RequestBody AlmacenRequest request) {
        return almacenService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('almacen:editar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        almacenService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
