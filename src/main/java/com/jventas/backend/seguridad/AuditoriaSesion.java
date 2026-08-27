package com.jventas.backend.seguridad;

import com.jventas.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Antes: gv_bitacora, registraba SO/arquitectura/versión de un cliente de
 * escritorio. Esas columnas siguen en la tabla (nullable, sin poblar desde
 * acá) porque son datos que ya no aplican a un login web; lo que sí importa
 * ahora es ip_address y user_agent, que sí se mapean.
 */
@Entity
@Table(name = "auditoria_sesion")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "fecha_actividad", nullable = false)
    private Instant fechaActividad;

    @PrePersist
    void prePersist() {
        if (fechaActividad == null) {
            fechaActividad = Instant.now();
        }
    }
}
