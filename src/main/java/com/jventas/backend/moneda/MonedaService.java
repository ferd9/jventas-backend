package com.jventas.backend.moneda;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonedaService {

    private final MonedaRepository monedaRepository;

    @Transactional(readOnly = true)
    public List<MonedaResponse> listarActivas() {
        return monedaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(MonedaResponse::from)
                .toList();
    }
}
