package com.jventas.backend.usuario;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepository.findAllConPermisos().stream().map(RolResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RolResponse obtener(Long id) {
        return RolResponse.from(obtenerEntidad(id));
    }

    @Transactional
    public RolResponse crear(RolRequest request) {
        Rol rol = new Rol();
        rol.setNombre(request.nombre());
        rol.setDescripcion(request.descripcion());
        rol.setPermisos(resolverPermisos(request.permisoIds()));
        return RolResponse.from(rolRepository.save(rol));
    }

    @Transactional
    public RolResponse actualizar(Long id, RolRequest request) {
        Rol rol = obtenerEntidad(id);
        rol.setNombre(request.nombre());
        rol.setDescripcion(request.descripcion());
        rol.setPermisos(resolverPermisos(request.permisoIds()));
        return RolResponse.from(rolRepository.save(rol));
    }

    private Rol obtenerEntidad(Long id) {
        return rolRepository.findByIdConPermisos(id).orElseThrow(() -> new NoSuchElementException("Rol no encontrado: " + id));
    }

    private Set<Permiso> resolverPermisos(Set<Long> permisoIds) {
        if (permisoIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Permiso> encontrados = permisoRepository.findAllById(permisoIds);
        if (encontrados.size() != permisoIds.size()) {
            Set<Long> encontradosIds = encontrados.stream().map(Permiso::getId).collect(Collectors.toSet());
            Set<Long> faltantes = new HashSet<>(permisoIds);
            faltantes.removeAll(encontradosIds);
            throw new NoSuchElementException("Permiso(s) no encontrado(s): " + faltantes);
        }
        return new HashSet<>(encontrados);
    }
}
