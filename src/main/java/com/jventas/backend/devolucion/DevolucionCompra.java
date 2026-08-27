package com.jventas.backend.devolucion;

import com.jventas.backend.compra.Compra;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Espejo de Devolucion (venta), del lado de compra: mercadería que se
 * devuelve al proveedor (defectuosa o de más). Documento propio, no edita
 * la compra original. Mismo plazo de 10 días desde la fecha de compra.
 */
@Entity
@Table(name = "devolucion_compra")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class DevolucionCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

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
