package com.jventas.backend.devolucion;

import com.jventas.backend.usuario.Usuario;
import com.jventas.backend.venta.Venta;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Documento propio, no edita la venta original -- decisión de negocio
 * confirmada: reingresa el stock automático (sin revisión previa) y
 * descuenta el reembolso directo del total de la venta. Plazo: 10 días
 * desde la fecha de venta.
 */
@Entity
@Table(name = "devolucion")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Instant fecha;

    private String motivo;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private boolean activo = true;

    @PrePersist
    void prePersist() {
        if (fecha == null) {
            fecha = Instant.now();
        }
    }
}
