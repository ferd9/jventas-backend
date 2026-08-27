package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarcaService {

    private final MarcaRepository marcaRepository;

    @Transactional
    public MarcaResponse crear(MarcaRequest request) {
        Marca marca = new Marca();
        marca.setNombre(request.nombre());
        return MarcaResponse.from(marcaRepository.save(marca));
    }

    @Transactional
    public MarcaResponse actualizar(Long id, MarcaRequest request) {
        Marca marca = marcaRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Marca no encontrada: " + id));
        marca.setNombre(request.nombre());
        return MarcaResponse.from(marcaRepository.save(marca));
    }
}
