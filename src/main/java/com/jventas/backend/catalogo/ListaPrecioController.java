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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listas-precio")
@RequiredArgsConstructor
public class ListaPrecioController {

    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioService listaPrecioService;

    @GetMapping
    public List<ListaPrecioResponse> listar() {
        return listaPrecioRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(ListaPrecioResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ResponseEntity<ListaPrecioResponse> crear(@Valid @RequestBody ListaPrecioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listaPrecioService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalogo:administrar')")
    public ListaPrecioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ListaPrecioRequest request) {
        return listaPrecioService.actualizar(id, request);
    }
}
