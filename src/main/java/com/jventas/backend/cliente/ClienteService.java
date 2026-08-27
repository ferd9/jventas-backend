package com.jventas.backend.cliente;

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
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final DireccionRepository direccionRepository;

    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String q, Pageable pageable) {
        Page<Cliente> pagina = StringUtils.hasText(q)
                ? clienteRepository.buscarPorNombreOApellidos(q, pageable)
                : clienteRepository.findByActivoTrue(pageable);
        return pagina.map(ClienteResponse::from);
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id) {
        return ClienteResponse.from(obtenerEntidad(id));
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        validar(request, null);

        Cliente cliente = new Cliente();
        aplicarCambios(cliente, request);
        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {
        Cliente cliente = obtenerEntidad(id);
        validar(request, cliente);
        aplicarCambios(cliente, request);
        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    @Transactional
    public void desactivar(Long id) {
        Cliente cliente = obtenerEntidad(id);
        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }

    @Transactional
    public void reactivar(Long id) {
        Cliente cliente = obtenerEntidad(id);
        cliente.setActivo(true);
        clienteRepository.save(cliente);
    }

    Cliente obtenerEntidad(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Cliente no encontrado: " + id));
    }

    private void validar(ClienteRequest request, Cliente actual) {
        if (!request.tieneIdentificacion()) {
            throw new IllegalArgumentException("El cliente necesita RUC o DNI");
        }
        if (request.ruc() != null
                && !request.ruc().isBlank()
                && (actual == null || !request.ruc().equals(actual.getRuc()))
                && clienteRepository.existsByRuc(request.ruc())) {
            throw new IllegalArgumentException("Ya existe un cliente con el RUC " + request.ruc());
        }
        if (request.dni() != null
                && !request.dni().isBlank()
                && (actual == null || !request.dni().equals(actual.getDni()))
                && clienteRepository.existsByDni(request.dni())) {
            throw new IllegalArgumentException("Ya existe un cliente con el DNI " + request.dni());
        }
    }

    private void aplicarCambios(Cliente cliente, ClienteRequest request) {
        cliente.setRuc(request.ruc());
        cliente.setDni(request.dni());
        cliente.setNombre(request.nombre());
        cliente.setApellidos(request.apellidos());
        cliente.setTipo(request.tipo());
        cliente.setEmail(request.email());
        cliente.setTelefono(request.telefono());
        cliente.setCelular(request.celular());
        cliente.setSexo(request.sexo());

        if (request.direccion() != null) {
            Direccion direccion = cliente.getDireccion() != null ? cliente.getDireccion() : new Direccion();
            request.direccion().aplicarA(direccion);
            cliente.setDireccion(direccionRepository.save(direccion));
        }
    }
}
