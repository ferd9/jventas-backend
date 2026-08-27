package com.jventas.backend.catalogo;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modelos")
@RequiredArgsConstructor
public class ModeloController {

    private final ModeloRepository modeloRepository;
    private final ModeloService modeloService;

    @GetMapping
    public List<ModeloResponse> listar(@RequestParam(required = false) Long marcaId) {
        List<Modelo> modelos = marcaId != null
                ? modeloRepository.findByMarcaIdAndActivoTrueOrderByNombreAsc(marcaId)
                : modeloRepository.findByActivoTrueOrderByNombreAsc();
        return modelos.stream().map(ModeloResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<ModeloResponse> crear(@Valid @RequestBody ModeloRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modeloService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ModeloResponse actualizar(@PathVariable Long id, @Valid @RequestBody ModeloRequest request) {
        return modeloService.actualizar(id, request);
    }
}
