package com.jventas.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Un doble clic o un reintento de red por timeout en "crear venta"/"crear
 * compra" puede generar dos documentos idénticos -- el backend no tenía
 * ninguna defensa propia contra eso. Si el cliente manda un header
 * `Idempotency-Key`, la primera respuesta exitosa se cachea y una segunda
 * request con la misma clave la recibe de vuelta tal cual, sin volver a
 * ejecutar el controller. Sin el header, el comportamiento es el de
 * siempre -- es un opt-in del cliente, no rompe nada existente.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> RUTAS_PROTEGIDAS = Set.of("/api/ventas", "/api/compras");

    private final IdempotencyKeyStore store;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clave = request.getHeader(HEADER);
        boolean aplica = "POST".equals(request.getMethod())
                && RUTAS_PROTEGIDAS.contains(request.getRequestURI())
                && clave != null
                && !clave.isBlank();

        if (!aplica) {
            filterChain.doFilter(request, response);
            return;
        }

        String claveCompuesta = claveCompuesta(request, clave);
        var cacheada = store.buscar(claveCompuesta);
        if (cacheada.isPresent()) {
            var respuesta = cacheada.get();
            response.setStatus(respuesta.status());
            if (respuesta.contentType() != null) {
                response.setContentType(respuesta.contentType());
            }
            response.getOutputStream().write(respuesta.cuerpo());
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);

        // solo se cachea una respuesta exitosa -- un 400 se puede corregir y reintentar
        // con la misma clave sin quedar pegado repitiendo el mismo error para siempre
        if (wrapper.getStatus() >= 200 && wrapper.getStatus() < 300) {
            store.guardar(claveCompuesta, wrapper.getStatus(), wrapper.getContentAsByteArray(), wrapper.getContentType());
        }
        wrapper.copyBodyToResponse();
    }

    private String claveCompuesta(HttpServletRequest request, String clave) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // la clave se separa por usuario -- sin esto, dos usuarios distintos usando el mismo
        // valor de Idempotency-Key (p.ej. un UUID generado con la misma semilla) se pisarían
        String usuario = auth != null ? auth.getName() : "anonimo";
        return usuario + "|" + request.getRequestURI() + "|" + clave;
    }
}
