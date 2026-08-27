package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetodoPagoService {

    private final MetodoPagoRepository metodoPagoRepository;

    @Transactional
    public MetodoPagoResponse crear(MetodoPagoRequest request) {
        MetodoPago metodoPago = new MetodoPago();
        metodoPago.setNombre(request.nombre());
        return MetodoPagoResponse.from(metodoPagoRepository.save(metodoPago));
    }

    @Transactional
    public MetodoPagoResponse actualizar(Long id, MetodoPagoRequest request) {
        MetodoPago metodoPago = metodoPagoRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Método de pago no encontrado: " + id));
        metodoPago.setNombre(request.nombre());
        return MetodoPagoResponse.from(metodoPagoRepository.save(metodoPago));
    }
}
