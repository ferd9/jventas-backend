package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModeloService {

    private final ModeloRepository modeloRepository;
    private final MarcaRepository marcaRepository;

    @Transactional
    public ModeloResponse crear(ModeloRequest request) {
        Modelo modelo = new Modelo();
        aplicarCambios(modelo, request);
        return ModeloResponse.from(modeloRepository.save(modelo));
    }

    @Transactional
    public ModeloResponse actualizar(Long id, ModeloRequest request) {
        Modelo modelo = modeloRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Modelo no encontrado: " + id));
        aplicarCambios(modelo, request);
        return ModeloResponse.from(modeloRepository.save(modelo));
    }

    private void aplicarCambios(Modelo modelo, ModeloRequest request) {
        modelo.setNombre(request.nombre());
        modelo.setMarca(marcaRepository.findById(request.marcaId())
                .orElseThrow(() -> new NoSuchElementException("Marca no encontrada: " + request.marcaId())));
    }
}
