package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImpuestoService {

    private final ImpuestoRepository impuestoRepository;

    @Transactional
    public ImpuestoResponse crear(ImpuestoRequest request) {
        Impuesto impuesto = new Impuesto();
        aplicarCambios(impuesto, request);
        return ImpuestoResponse.from(impuestoRepository.save(impuesto));
    }

    @Transactional
    public ImpuestoResponse actualizar(Long id, ImpuestoRequest request) {
        Impuesto impuesto = impuestoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Impuesto no encontrado: " + id));
        aplicarCambios(impuesto, request);
        return ImpuestoResponse.from(impuestoRepository.save(impuesto));
    }

    private void aplicarCambios(Impuesto impuesto, ImpuestoRequest request) {
        impuesto.setNombre(request.nombre());
        impuesto.setTasa(request.tasa());

        // Solo puede haber un impuesto predeterminado (indice unico parcial en la tabla).
        // Hibernate agrupa todos los INSERT antes que los UPDATE al hacer flush, sin
        // importar el orden en que se llamó a save() -- sin el flush explícito acá, el
        // INSERT de este impuesto (es_default=true) se ejecuta antes que el UPDATE que le
        // quita el default al anterior, y viola el índice antes de que la transacción
        // termine de acomodar todo. Se probó y reprodujo el 409 antes de este fix.
        if (request.esDefault() && !impuesto.isEsDefault()) {
            impuestoRepository.findByEsDefaultTrue().ifPresent(anterior -> {
                anterior.setEsDefault(false);
                impuestoRepository.saveAndFlush(anterior);
            });
        }
        impuesto.setEsDefault(request.esDefault());
    }
}
