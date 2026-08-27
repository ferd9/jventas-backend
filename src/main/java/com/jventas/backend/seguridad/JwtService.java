package com.jventas.backend.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(JwtProperties properties, Environment environment) {
        this.expirationMinutes = properties.expirationMinutes();
        // "${JWT_SECRET}" sin default en application-prod.yml no falla el
        // arranque si la variable no existe -- Spring deja el placeholder
        // sin resolver como texto literal en vez de lanzar una excepción
        // (comprobado en la práctica). Sin este chequeo explícito, un
        // despliegue en prod sin JWT_SECRET firmaría tokens con esa clave
        // literal (débil y predecible) en vez de fallar ruidosamente.
        boolean secretoInvalido =
                properties.secret() == null || properties.secret().isBlank() || properties.secret().contains("${");
        if (secretoInvalido && environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "JWT_SECRET no está definido en este entorno de producción. Sin esto, cada reinicio "
                            + "invalidaría todas las sesiones activas y/o firmaría tokens con una clave predecible.");
        }
        if (secretoInvalido) {
            log.warn("""

                    ============================================================
                    JWT_SECRET no está configurado: se generó uno aleatorio SOLO
                    para esta ejecución. Los tokens emitidos ahora dejarán de ser
                    válidos en el próximo reinicio. Define JWT_SECRET en cualquier
                    entorno que no sea tu máquina local.
                    ============================================================
                    """);
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        }
    }

    public long expirationSeconds() {
        return expirationMinutes * 60;
    }

    public String generar(String login, Collection<String> authorities) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(login)
                .claim("authorities", authorities)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
    }

    public Optional<Jws<Claims>> validar(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT inválido: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
