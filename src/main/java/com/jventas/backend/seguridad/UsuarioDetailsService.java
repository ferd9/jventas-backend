package com.jventas.backend.seguridad;

import com.jventas.backend.usuario.Permiso;
import com.jventas.backend.usuario.Rol;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo se usa en /api/auth/login para verificar la contraseña. Las requests
 * posteriores se autorizan con las autoridades ya embebidas en el JWT
 * (JwtAuthenticationFilter), sin volver a tocar la base de datos.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(login)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + login));

        return User.withUsername(usuario.getLogin())
                .password(usuario.getPasswordHash())
                .authorities(construirAuthorities(usuario))
                .build();
    }

    static List<GrantedAuthority> construirAuthorities(Usuario usuario) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Rol rol : usuario.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.getNombre()));
            for (Permiso permiso : rol.getPermisos()) {
                authorities.add(new SimpleGrantedAuthority(permiso.getCodigo()));
            }
        }
        return new ArrayList<>(authorities);
    }
}
