package com.unh.mantenimiento.mantenimiento.model;

import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.usuario.model.ResponsableUnh;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad MantenimientoUnh: un trabajo de mantenimiento sobre un activo.
 *
 * CORRECTIVO: siempre tiene incidencia (CHECK en la BD). Se crea a partir de
 * una incidencia y la pone en EN_ATENCION.
 * PREVENTIVO: nunca tiene incidencia. Se registra a mano.
 *
 * Al crear el mantenimiento el activo pasa a EN_MANTENIMIENTO; al finalizarlo
 * vuelve a OPERATIVO y la incidencia se cierra.
 */
@Entity
@Table(name = "mantenimiento_unh")
@Getter
@Setter
@NoArgsConstructor
public class MantenimientoUnh {

    /** El trabajo esta en curso. */
    public static final String EN_PROCESO = "EN_PROCESO";

    /** El trabajo termino. */
    public static final String FINALIZADO = "FINALIZADO";

    /** Tipo de trabajo. */
    public static final String CORRECTIVO = "CORRECTIVO";
    public static final String PREVENTIVO = "PREVENTIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mantenimiento")
    private Integer idMantenimiento;

    /** El activo que se mantiene. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false)
    private ActivoUnh activo;

    /** Quien hizo el trabajo: el usuario con sesion iniciada. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_responsable", nullable = false)
    private ResponsableUnh responsable;

    /** La incidencia que origino el trabajo. NULL en los preventivos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_incidencia")
    private IncidenciaUnh incidencia;

    @Column(name = "tipo_mantenimiento", nullable = false, length = 15)
    private String tipoMantenimiento = CORRECTIVO;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    /** Se llena al finalizar el trabajo. */
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "diagnostico", length = 1000)
    private String diagnostico;

    @Column(name = "actividad_realizada", length = 1000)
    private String actividadRealizada;

    @Column(name = "resultado", length = 500)
    private String resultado;

    @Column(name = "estado_mantenimiento", nullable = false, length = 15)
    private String estadoMantenimiento = EN_PROCESO;

    @Column(name = "horas_inactividad", precision = 10, scale = 2)
    private BigDecimal horasInactividad;

    @Column(name = "costo_mano_obra", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoManoObra = BigDecimal.ZERO;

    @Column(name = "proxima_fecha_mantenimiento")
    private LocalDate proximaFechaMantenimiento;

    @Column(name = "observaciones_mantenimiento", length = 500)
    private String observaciones;

    public boolean esEnProceso() {
        return EN_PROCESO.equals(estadoMantenimiento);
    }

    public boolean esCorrectivo() {
        return CORRECTIVO.equals(tipoMantenimiento);
    }
}
