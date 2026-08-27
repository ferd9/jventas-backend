package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional
    public UnidadMedidaResponse crear(UnidadMedidaRequest request) {
        UnidadMedida unidad = new UnidadMedida();
        aplicarCambios(unidad, request);
        return UnidadMedidaResponse.from(unidadMedidaRepository.save(unidad));
    }

    @Transactional
    public UnidadMedidaResponse actualizar(Long id, UnidadMedidaRequest request) {
        UnidadMedida unidad = unidadMedidaRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad de medida no encontrada: " + id));
        aplicarCambios(unidad, request);
        return UnidadMedidaResponse.from(unidadMedidaRepository.save(unidad));
    }

    private void aplicarCambios(UnidadMedida unidad, UnidadMedidaRequest request) {
        unidad.setNombre(request.nombre());
        unidad.setAbreviatura(request.abreviatura());
    }
}
