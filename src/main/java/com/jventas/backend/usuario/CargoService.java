package com.jventas.backend.usuario;

import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CargoService {

    private final CargoRepository cargoRepository;

    @Transactional(readOnly = true)
    public List<CargoResponse> listar() {
        return cargoRepository.findByActivoTrueOrderByNombreAsc().stream().map(CargoResponse::from).toList();
    }

    @Transactional
    public CargoResponse crear(CargoRequest request) {
        Cargo cargo = new Cargo();
        cargo.setNombre(request.nombre());
        return CargoResponse.from(cargoRepository.save(cargo));
    }

    @Transactional
    public CargoResponse actualizar(Long id, CargoRequest request) {
        Cargo cargo = cargoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Cargo no encontrado: " + id));
        cargo.setNombre(request.nombre());
        return CargoResponse.from(cargoRepository.save(cargo));
    }
}
