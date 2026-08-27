package com.jventas.backend.seguridad;

import com.jventas.backend.usuario.Cargo;
import com.jventas.backend.usuario.CargoRepository;
import com.jventas.backend.usuario.Rol;
import com.jventas.backend.usuario.RolRepository;
import com.jventas.backend.usuario.SexoPersona;
import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.usuario.UsuarioRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el administrador inicial solo cuando la tabla usuario está vacía —
 * nunca pisa datos existentes. Nunca hay una contraseña hardcodeada en el
 * repositorio: si no se define ADMIN_PASSWORD se genera una aleatoria y se
 * imprime una única vez en el log de arranque.
 */
@Slf4j
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CargoRepository cargoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jventas.bootstrap.admin.login:admin}")
    private String adminLogin;

    @Value("${jventas.bootstrap.admin.password:}")
    private String adminPassword;

    public BootstrapAdminRunner(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            CargoRepository cargoRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.cargoRepository = cargoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return; // ya hay usuarios: no es una instalación nueva, no tocar nada
        }

        String password = adminPassword.isBlank() ? generarPasswordAleatoria() : adminPassword;

        Cargo cargo = cargoRepository
                .findByNombre("Múltiple")
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el cargo 'Múltiple' — ¿corrieron las migraciones de Flyway (V2)?"));
        Rol rolAdministrador = rolRepository
                .findByNombre("ADMINISTRADOR")
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol 'ADMINISTRADOR' — ¿corrieron las migraciones de Flyway (V2)?"));

        Usuario admin = new Usuario();
        admin.setDni("00000000");
        admin.setCodigo("ADMIN");
        admin.setLogin(adminLogin);
        admin.setNombre("Administrador");
        admin.setApellidos("del sistema");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTelefono("000000000");
        admin.setSexo(SexoPersona.H);
        admin.setCargo(cargo);
        admin.setActivo(true);
        admin.setRoles(Set.of(rolAdministrador));

        usuarioRepository.save(admin);

        if (adminPassword.isBlank()) {
            log.warn(
                    """

                    ============================================================
                    Se creó el usuario administrador inicial:
                      login:    {}
                      password: {}   (generada, cámbiala en cuanto entres)
                    ============================================================
                    """,
                    adminLogin,
                    password);
        } else {
            log.info("Se creó el usuario administrador inicial con login '{}'", adminLogin);
        }
    }

    private String generarPasswordAleatoria() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
