package com.jventas.backend.proveedor;

import com.jventas.backend.direccion.Direccion;
import com.jventas.backend.direccion.DireccionRepository;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final DireccionRepository direccionRepository;

    @Transactional(readOnly = true)
    public Page<ProveedorResponse> listar(String q, Pageable pageable) {
        Page<Proveedor> pagina = StringUtils.hasText(q)
                ? proveedorRepository.findByActivoTrueAndRazonSocialContainingIgnoreCase(q, pageable)
                : proveedorRepository.findByActivoTrue(pageable);
        return pagina.map(ProveedorResponse::from);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse obtener(Long id) {
        return ProveedorResponse.from(obtenerEntidad(id));
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        if (proveedorRepository.existsByRuc(request.ruc())) {
            throw new IllegalArgumentException("Ya existe un proveedor con el RUC " + request.ruc());
        }
        Direccion direccion = direccionRepository.save(request.direccion().toEntity());

        Proveedor proveedor = new Proveedor();
        aplicarCambios(proveedor, request);
        proveedor.setDireccion(direccion);
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = obtenerEntidad(id);
        if (!proveedor.getRuc().equals(request.ruc()) && proveedorRepository.existsByRuc(request.ruc())) {
            throw new IllegalArgumentException("Ya existe un proveedor con el RUC " + request.ruc());
        }
        aplicarCambios(proveedor, request);
        request.direccion().aplicarA(proveedor.getDireccion());
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    public void desactivar(Long id) {
        Proveedor proveedor = obtenerEntidad(id);
        proveedor.setActivo(false);
        proveedorRepository.save(proveedor);
    }

    @Transactional
    public void reactivar(Long id) {
        Proveedor proveedor = obtenerEntidad(id);
        proveedor.setActivo(true);
        proveedorRepository.save(proveedor);
    }

    Proveedor obtenerEntidad(Long id) {
        return proveedorRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Proveedor no encontrado: " + id));
    }

    private void aplicarCambios(Proveedor proveedor, ProveedorRequest request) {
        proveedor.setRuc(request.ruc());
        proveedor.setRazonSocial(request.razonSocial());
        proveedor.setTelefono(request.telefono());
        proveedor.setTelefonoAlternativo(request.telefonoAlternativo());
        proveedor.setCuentaBancaria(request.cuentaBancaria());
        proveedor.setNombreContacto(request.nombreContacto());
        proveedor.setEmail(request.email());
        proveedor.setRubro(request.rubro());
    }
}
