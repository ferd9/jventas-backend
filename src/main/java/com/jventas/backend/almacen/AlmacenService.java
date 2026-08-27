package com.jventas.backend.almacen;

import com.jventas.backend.direccion.Direccion;
import com.jventas.backend.direccion.DireccionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlmacenService {

    private final AlmacenRepository almacenRepository;
    private final DireccionRepository direccionRepository;

    @Transactional(readOnly = true)
    public List<AlmacenResponse> listar() {
        return almacenRepository.findByActivoTrueOrderByNombreAsc().stream().map(AlmacenResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlmacenResponse obtener(Long id) {
        return AlmacenResponse.from(obtenerEntidad(id));
    }

    @Transactional
    public AlmacenResponse crear(AlmacenRequest request) {
        Direccion direccion = direccionRepository.save(request.direccion().toEntity());
        Almacen almacen = new Almacen();
        almacen.setNombre(request.nombre());
        almacen.setDireccion(direccion);
        return AlmacenResponse.from(almacenRepository.save(almacen));
    }

    @Transactional
    public AlmacenResponse actualizar(Long id, AlmacenRequest request) {
        Almacen almacen = obtenerEntidad(id);
        almacen.setNombre(request.nombre());
        request.direccion().aplicarA(almacen.getDireccion());
        return AlmacenResponse.from(almacenRepository.save(almacen));
    }

    @Transactional
    public void eliminar(Long id) {
        Almacen almacen = obtenerEntidad(id);
        almacen.setActivo(false);
        almacenRepository.save(almacen);
    }

    Almacen obtenerEntidad(Long id) {
        return almacenRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Almacén no encontrado: " + id));
    }
}
