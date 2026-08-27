package com.jventas.backend.seguridad;

import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaSesionRepository auditoriaSesionRepository;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimiter.verificarNoBloqueado(request.login());

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login(), request.password()));
        } catch (AuthenticationException ex) {
            loginRateLimiter.registrarFallo(request.login());
            throw ex;
        }
        loginRateLimiter.registrarExito(request.login());

        Usuario usuario = usuarioRepository.findByLoginAndActivoTrue(auth.getName()).orElseThrow();
        List<String> authorities = authoridadesDeNegocio(auth);
        String accessToken = jwtService.generar(auth.getName(), authorities);
        String refreshToken = refreshTokenService.generar(usuario);

        registrarAuditoria(usuario, httpRequest);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.expirationSeconds(),
                refreshToken,
                usuario.getLogin(),
                usuario.getNombre() + " " + usuario.getApellidos(),
                authorities);
    }

    /** No pide contraseña de nuevo -- solo un refresh token válido, sin vencer y sin revocar. */
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        Usuario usuario = refreshTokenService.validar(request.refreshToken());
        List<String> authorities = UsuarioDetailsService.construirAuthorities(usuario).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String accessToken = jwtService.generar(usuario.getLogin(), authorities);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.expirationSeconds(),
                request.refreshToken(),
                usuario.getLogin(),
                usuario.getNombre() + " " + usuario.getApellidos(),
                authorities);
    }

    /** Revoca el refresh token -- el access token ya emitido sigue vivo hasta que expire solo (a lo sumo `expiration-minutes`). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revocar(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioPerfilResponse me(Authentication authentication) {
        Usuario usuario = usuarioRepository
                .findByLoginAndActivoTrue(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        List<String> authorities = authoridadesDeNegocio(authentication);

        return new UsuarioPerfilResponse(usuario.getLogin(), usuario.getNombre() + " " + usuario.getApellidos(), authorities);
    }

    private void registrarAuditoria(Usuario usuario, HttpServletRequest httpRequest) {
        AuditoriaSesion sesion = new AuditoriaSesion();
        sesion.setUsuario(usuario);
        sesion.setIpAddress(obtenerIp(httpRequest));
        sesion.setUserAgent(httpRequest.getHeader(HttpHeaders.USER_AGENT));
        auditoriaSesionRepository.save(sesion);
    }

    private String obtenerIp(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Spring Security agrega autoridades internas (p.ej. "FACTOR_PASSWORD",
     * para step-up auth) al resultado de la autenticación — no son permisos
     * de negocio y no deben filtrarse a los clientes de la API.
     */
    private static List<String> authoridadesDeNegocio(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("FACTOR_"))
                .toList();
    }
}
