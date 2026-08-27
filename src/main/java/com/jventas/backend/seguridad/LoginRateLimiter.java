package com.jventas.backend.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * En memoria, por instancia -- suficiente para un solo servidor. Si esto
 * corre alguna vez detrás de varias instancias, hay que moverlo a algo
 * compartido (Redis); documentado acá para no descubrirlo tarde.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA_BLOQUEO = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Intentos> porLogin = new ConcurrentHashMap<>();

    public void verificarNoBloqueado(String login) {
        Intentos intentos = porLogin.get(normalizar(login));
        if (intentos == null) {
            return;
        }
        synchronized (intentos) {
            if (intentos.fallos >= MAX_INTENTOS && Instant.now().isBefore(intentos.bloqueadoHasta)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
            }
        }
    }

    public void registrarFallo(String login) {
        Intentos intentos = porLogin.computeIfAbsent(normalizar(login), k -> new Intentos());
        synchronized (intentos) {
            intentos.fallos++;
            if (intentos.fallos >= MAX_INTENTOS) {
                intentos.bloqueadoHasta = Instant.now().plus(VENTANA_BLOQUEO);
            }
        }
    }

    public void registrarExito(String login) {
        porLogin.remove(normalizar(login));
    }

    private String normalizar(String login) {
        return login == null ? "" : login.toLowerCase(Locale.ROOT);
    }

    private static class Intentos {
        int fallos;
        Instant bloqueadoHasta = Instant.EPOCH;
    }
}
