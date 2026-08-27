package com.jventas.backend.documento;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.almacen.AlmacenRepository;
import com.jventas.backend.catalogo.TipoDocumento;
import com.jventas.backend.catalogo.TipoDocumentoRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SerieDocumentoService {

    private final SerieDocumentoRepository serieDocumentoRepository;
    private final AlmacenRepository almacenRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;

    @Transactional(readOnly = true)
    public List<SerieDocumentoResponse> listar(Long almacenId, Long tipoDocumentoId) {
        return serieDocumentoRepository.findByActivoTrueAndAlmacenIdAndTipoDocumentoId(almacenId, tipoDocumentoId).stream()
                .map(SerieDocumentoResponse::from)
                .toList();
    }

    @Transactional
    public SerieDocumentoResponse crear(SerieDocumentoRequest request) {
        Almacen almacen = almacenRepository
                .findById(request.almacenId())
                .orElseThrow(() -> new NoSuchElementException("Almacén no encontrado: " + request.almacenId()));
        TipoDocumento tipoDocumento = tipoDocumentoRepository
                .findById(request.tipoDocumentoId())
                .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado: " + request.tipoDocumentoId()));

        SerieDocumento serieDocumento = new SerieDocumento();
        serieDocumento.setAlmacen(almacen);
        serieDocumento.setTipoDocumento(tipoDocumento);
        serieDocumento.setSerie(request.serie());
        return SerieDocumentoResponse.from(serieDocumentoRepository.save(serieDocumento));
    }

    /**
     * Se llama desde dentro de la misma transacción que crea la compra/venta
     * (propagación REQUIRED por defecto) — si el resto de la operación falla,
     * el incremento del correlativo se revierte también, nunca queda un
     * número "quemado" sin comprobante real detrás.
     */
    @Transactional
    public SerieDocumento consumirSiguiente(Long serieDocumentoId, Long almacenIdEsperado, Long tipoDocumentoIdEsperado) {
        SerieDocumento serieDocumento = serieDocumentoRepository
                .findByIdParaActualizar(serieDocumentoId)
                .orElseThrow(() -> new NoSuchElementException("Serie de documento no encontrada: " + serieDocumentoId));

        if (!serieDocumento.getAlmacen().getId().equals(almacenIdEsperado)
                || !serieDocumento.getTipoDocumento().getId().equals(tipoDocumentoIdEsperado)) {
            throw new IllegalArgumentException("La serie " + serieDocumento.getSerie()
                    + " no corresponde a ese almacén/tipo de documento");
        }

        serieDocumento.setCorrelativoActual(serieDocumento.getCorrelativoActual() + 1);
        return serieDocumentoRepository.save(serieDocumento);
    }
}
