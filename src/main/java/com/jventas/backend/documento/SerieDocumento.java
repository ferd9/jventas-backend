package com.jventas.backend.documento;

import com.jventas.backend.almacen.Almacen;
import com.jventas.backend.catalogo.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Numeración correlativa real (F001-00000123) por almacén y tipo de documento. */
@Entity
@Table(name = "serie_documento")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SerieDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "almacen_id", nullable = false)
    private Almacen almacen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_documento_id", nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(nullable = false)
    private String serie;

    @Column(name = "correlativo_actual", nullable = false)
    private int correlativoActual;

    @Column(nullable = false)
    private boolean activo = true;

    /** Formatea SIN consumir el correlativo — para mostrar "el próximo sería...". Usar consumirSiguiente() para emitir de verdad. */
    public String formatear(int correlativo) {
        return serie + "-" + String.format("%08d", correlativo);
    }
}
