package com.jventas.backend.usuario;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lectura abierta a cualquier autenticado -- lo necesita el formulario de creación de usuario. */
@RestController
@RequestMapping("/api/cargos")
@RequiredArgsConstructor
public class CargoController {

    private final CargoService cargoService;

    @GetMapping
    public List<CargoResponse> listar() {
        return cargoService.listar();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public ResponseEntity<CargoResponse> crear(@Valid @RequestBody CargoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cargoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('usuario:administrar')")
    public CargoResponse actualizar(@PathVariable Long id, @Valid @RequestBody CargoRequest request) {
        return cargoService.actualizar(id, request);
    }
}
