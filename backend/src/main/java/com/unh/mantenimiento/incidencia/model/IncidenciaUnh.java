package com.unh.mantenimiento.incidencia.model;

import com.unh.mantenimiento.activo.model.ActivoUnh;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad IncidenciaUnh: una falla reportada sobre un activo.
 *
 * La incidencia nace SIEMPRE en estado ABIERTA. El paso a EN_ATENCION y a
 * CERRADA lo hace el modulo de mantenimientos (I5) de forma automatica: una
 * incidencia no se cierra a mano.
 */
@Entity
@Table(name = "incidencia_unh")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaUnh {

    /** Estado inicial: recien reportada. */
    public static final String ABIERTA = "ABIERTA";

    /** Alguien tomo la incidencia y esta en proceso de atencion. */
    public static final String EN_ATENCION = "EN_ATENCION";

    /** Resuelta. La fija el cierre del mantenimiento (I5). */
    public static final String CERRADA = "CERRADA";

    /** Prioridad. */
    public static final String ALTA = "ALTA";
    public static final String MEDIA = "MEDIA";
    public static final String BAJA = "BAJA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_incidencia")
    private Integer idIncidencia;

    /** Toda incidencia pertenece a un activo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false)
    private ActivoUnh activo;

    /** Cuando se detecto la falla. */
    @Column(name = "fecha_deteccion_incidencia", nullable = false)
    private LocalDateTime fechaDeteccion;

    @Column(name = "descripcion", nullable = false, length = 1000)
    private String descripcion;

    @Column(name = "prioridad_incidencia", nullable = false, length = 10)
    private String prioridad;

    @Column(name = "estado_incidencia", nullable = false, length = 15)
    private String estado = ABIERTA;

    /** Se llena al cerrar la incidencia (I5). */
    @Column(name = "fecha_cierre_incidencia")
    private LocalDateTime fechaCierre;

    @Column(name = "observaciones_incidencia", length = 500)
    private String observaciones;

    /** True solo mientras la incidencia esta abierta y es editable. */
    public boolean esAbierta() {
        return ABIERTA.equals(estado);
    }
}
