package com.jventas.backend.almacen;

import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EncargadoAlmacenService {

    private final EncargadoAlmacenRepository encargadoAlmacenRepository;
    private final AlmacenRepository almacenRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<EncargadoAlmacenResponse> porAlmacen(Long almacenId) {
        return encargadoAlmacenRepository.findByActivoTrueAndAlmacenId(almacenId).stream()
                .map(EncargadoAlmacenResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EncargadoAlmacenResponse> porUsuario(Long usuarioId) {
        return encargadoAlmacenRepository.findByActivoTrueAndUsuarioId(usuarioId).stream()
                .map(EncargadoAlmacenResponse::from)
                .toList();
    }

    @Transactional
    public EncargadoAlmacenResponse asignar(EncargadoAlmacenRequest request) {
        Usuario usuario = usuarioRepository
                .findById(request.usuarioId())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + request.usuarioId()));
        Almacen almacen = almacenRepository
                .findById(request.almacenId())
                .orElseThrow(() -> new NoSuchElementException("Almacén no encontrado: " + request.almacenId()));

        EncargadoAlmacen encargado = encargadoAlmacenRepository
                .findByUsuarioIdAndAlmacenId(request.usuarioId(), request.almacenId())
                .orElseGet(EncargadoAlmacen::new);
        encargado.setUsuario(usuario);
        encargado.setAlmacen(almacen);
        encargado.setTipoCargo(request.tipoCargo());
        encargado.setActivo(true);

        return EncargadoAlmacenResponse.from(encargadoAlmacenRepository.save(encargado));
    }

    @Transactional
    public void quitar(Long id) {
        EncargadoAlmacen encargado = encargadoAlmacenRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Asignación no encontrada: " + id));
        encargado.setActivo(false);
        encargadoAlmacenRepository.save(encargado);
    }
}
