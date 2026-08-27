package com.jventas.backend.usuario;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CargoRepository cargoRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAllByOrderByNombreAsc(pageable).map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.from(obtenerEntidad(id));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioCrearRequest request) {
        validarUnicidad(request.dni(), request.codigo(), request.login(), null);
        // (null = no hay usuario propio a excluir, es un alta nueva)

        Usuario usuario = new Usuario();
        usuario.setDni(request.dni());
        usuario.setCodigo(request.codigo());
        usuario.setLogin(request.login());
        usuario.setNombre(request.nombre());
        usuario.setApellidos(request.apellidos());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setFechaNacimiento(request.fechaNacimiento());
        usuario.setTelefono(request.telefono());
        usuario.setTelefono2(request.telefono2());
        usuario.setCelular(request.celular());
        usuario.setEmail(request.email());
        usuario.setSexo(request.sexo());
        usuario.setCargo(referenciaCargo(request.cargoId()));
        usuario.setDescripcion(request.descripcion());
        usuario.setRoles(referenciaRoles(request.rolIds()));

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioActualizarRequest request) {
        Usuario usuario = obtenerEntidad(id);
        validarUnicidad(request.dni(), request.codigo(), request.login(), usuario);

        usuario.setDni(request.dni());
        usuario.setCodigo(request.codigo());
        usuario.setLogin(request.login());
        usuario.setNombre(request.nombre());
        usuario.setApellidos(request.apellidos());
        usuario.setFechaNacimiento(request.fechaNacimiento());
        usuario.setTelefono(request.telefono());
        usuario.setTelefono2(request.telefono2());
        usuario.setCelular(request.celular());
        usuario.setEmail(request.email());
        usuario.setSexo(request.sexo());
        usuario.setCargo(referenciaCargo(request.cargoId()));
        usuario.setDescripcion(request.descripcion());
        usuario.setRoles(referenciaRoles(request.rolIds()));

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public void desactivar(Long id) {
        Usuario usuario = obtenerEntidad(id);
        usuario.setActivo(false);
        usuario.setFechaBaja(Instant.now());
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void reactivar(Long id) {
        Usuario usuario = obtenerEntidad(id);
        usuario.setActivo(true);
        usuario.setFechaBaja(null);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void cambiarPasswordPropia(String loginUsuario, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(loginUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + loginUsuario));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public ResetearPasswordResponse resetearPassword(Long id, ResetearPasswordRequest request) {
        Usuario usuario = obtenerEntidad(id);
        String nueva = request.passwordNueva() != null && !request.passwordNueva().isBlank()
                ? request.passwordNueva()
                : generarPasswordAleatoria();
        usuario.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(usuario);
        return new ResetearPasswordResponse(nueva);
    }

    private void validarUnicidad(String dni, String codigo, String login, Usuario actual) {
        if (choca(usuarioRepository.existsByDni(dni), actual, u -> u.getDni().equals(dni))) {
            throw new IllegalArgumentException("Ya existe un usuario con el DNI " + dni);
        }
        if (choca(usuarioRepository.existsByCodigo(codigo), actual, u -> u.getCodigo().equals(codigo))) {
            throw new IllegalArgumentException("Ya existe un usuario con el código " + codigo);
        }
        if (choca(usuarioRepository.existsByLogin(login), actual, u -> u.getLogin().equals(login))) {
            throw new IllegalArgumentException("Ya existe un usuario con el login " + login);
        }
    }

    private boolean choca(boolean existe, Usuario actual, java.util.function.Predicate<Usuario> mismoValorQueActual) {
        if (!existe) {
            return false;
        }
        if (actual == null) {
            return true; // creando: cualquier coincidencia es un choque
        }
        // editando: solo es choque si el valor cambió Y pertenece a OTRO usuario
        return !mismoValorQueActual.test(actual);
    }

    private Usuario obtenerEntidad(Long id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }

    private Cargo referenciaCargo(Long id) {
        return cargoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Cargo no encontrado: " + id));
    }

    private Set<Rol> referenciaRoles(List<Long> rolIds) {
        if (rolIds == null || rolIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Rol> roles = new HashSet<>();
        for (Long rolId : rolIds) {
            roles.add(rolRepository.findById(rolId).orElseThrow(() -> new NoSuchElementException("Rol no encontrado: " + rolId)));
        }
        return roles;
    }

    private String generarPasswordAleatoria() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
