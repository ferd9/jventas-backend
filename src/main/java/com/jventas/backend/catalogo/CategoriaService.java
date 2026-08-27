package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        Categoria categoria = new Categoria();
        aplicarCambios(categoria, request);
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Categoría no encontrada: " + id));
        aplicarCambios(categoria, request);
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }

    private void aplicarCambios(Categoria categoria, CategoriaRequest request) {
        categoria.setNombre(request.nombre());
        categoria.setRequiereSerie(request.requiereSerie());
        if (request.categoriaPadreId() != null) {
            if (request.categoriaPadreId().equals(categoria.getId())) {
                throw new IllegalArgumentException("Una categoría no puede ser su propia categoría padre");
            }
            categoria.setCategoriaPadre(categoriaRepository
                    .findById(request.categoriaPadreId())
                    .orElseThrow(() -> new NoSuchElementException("Categoría padre no encontrada: " + request.categoriaPadreId())));
        } else {
            categoria.setCategoriaPadre(null);
        }
    }
}
