package com.jventas.backend.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * En memoria, por instancia -- mismo enfoque y misma limitación documentada
 * que LoginRateLimiter: suficiente para un solo servidor, hay que mover a
 * algo compartido (Redis) si esto corre alguna vez detrás de varias
 * instancias.
 */
@Component
public class IdempotencyKeyStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    public record RespuestaCacheada(int status, byte[] cuerpo, String contentType) {}

    private record Entrada(RespuestaCacheada respuesta, Instant expiraEn) {}

    private final ConcurrentMap<String, Entrada> cache = new ConcurrentHashMap<>();

    public Optional<RespuestaCacheada> buscar(String clave) {
        Entrada entrada = cache.get(clave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (entrada.expiraEn().isBefore(Instant.now())) {
            cache.remove(clave);
            return Optional.empty();
        }
        return Optional.of(entrada.respuesta());
    }

    public void guardar(String clave, int status, byte[] cuerpo, String contentType) {
        cache.put(clave, new Entrada(new RespuestaCacheada(status, cuerpo, contentType), Instant.now().plus(TTL)));
    }
}
