package com.jventas.backend.seguridad;

import com.jventas.backend.usuario.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El access token (JWT) dura poco a propósito -- la sesión larga la
 * sostiene este token opaco, guardado en base de datos solo como hash.
 * Validar/revocar sí implica una consulta a la base -- pero solo pasa una
 * vez cada `expiration-minutes`, no en cada request como el access token.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String generar(Usuario usuario) {
        String raw = generarValorAleatorio();

        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setTokenHash(hash(raw));
        token.setExpiraEn(Instant.now().plus(jwtProperties.refreshExpirationDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(token);

        return raw;
    }

    @Transactional
    public Usuario validar(String raw) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHashAndRevocadoFalse(hash(raw))
                .orElseThrow(() -> new RefreshTokenException("Refresh token inválido"));

        if (token.getExpiraEn().isBefore(Instant.now())) {
            throw new RefreshTokenException("Refresh token expirado, inicia sesión de nuevo");
        }
        Usuario usuario = token.getUsuario();
        if (!usuario.isActivo()) {
            throw new RefreshTokenException("El usuario ya no está activo");
        }

        // el controller arma las authorities fuera de esta transacción -- sin
        // esto, roles/permisos (lazy) revientan con LazyInitializationException
        Hibernate.initialize(usuario.getRoles());
        usuario.getRoles().forEach(rol -> Hibernate.initialize(rol.getPermisos()));

        return usuario;
    }

    @Transactional
    public void revocar(String raw) {
        refreshTokenRepository.findByTokenHashAndRevocadoFalse(hash(raw)).ifPresent(token -> {
            token.setRevocado(true);
            refreshTokenRepository.save(token);
        });
    }

    private String generarValorAleatorio() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
