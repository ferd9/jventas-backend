package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoDocumentoService {

    private final TipoDocumentoRepository tipoDocumentoRepository;

    @Transactional
    public TipoDocumentoResponse crear(TipoDocumentoRequest request) {
        TipoDocumento tipo = new TipoDocumento();
        aplicarCambios(tipo, request);
        return TipoDocumentoResponse.from(tipoDocumentoRepository.save(tipo));
    }

    @Transactional
    public TipoDocumentoResponse actualizar(Long id, TipoDocumentoRequest request) {
        TipoDocumento tipo = tipoDocumentoRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado: " + id));
        aplicarCambios(tipo, request);
        return TipoDocumentoResponse.from(tipoDocumentoRepository.save(tipo));
    }

    private void aplicarCambios(TipoDocumento tipo, TipoDocumentoRequest request) {
        tipo.setNombre(request.nombre());
        tipo.setAplicaCompra(request.aplicaCompra());
        tipo.setAplicaVenta(request.aplicaVenta());
    }
}
