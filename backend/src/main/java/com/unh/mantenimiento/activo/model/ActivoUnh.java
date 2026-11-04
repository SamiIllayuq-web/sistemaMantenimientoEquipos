package com.unh.mantenimiento.activo.model;

import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad ActivoUnh: los equipos e infraestructura de la residencia.
 *
 * 'estado' (ACTIVO|INACTIVO) es el estado de GESTION: un activo desactivado
 * sigue inventariado, nunca se borra.
 * 'estado_operativo' (OPERATIVO|EN_MANTENIMIENTO|FUERA_DE_SERVICIO) es el
 * estado TECNICO del equipo. Son cosas distintas.
 */
@Entity
@Table(name = "activo_unh")
@Getter
@Setter
@NoArgsConstructor
public class ActivoUnh {

    /** Estado de gestion. */
    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_INACTIVO = "INACTIVO";

    /** Estado tecnico del equipo. */
    public static final String OPERATIVO = "OPERATIVO";
    public static final String EN_MANTENIMIENTO = "EN_MANTENIMIENTO";
    public static final String FUERA_DE_SERVICIO = "FUERA_DE_SERVICIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_activo")
    private Integer idActivo;

    /** Todo activo tiene tipo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_activo", nullable = false)
    private TipoActivoUnh tipoActivo;

    /** Todo activo tiene ubicacion. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ubicacion", nullable = false)
    private UbicacionUnh ubicacion;

    @Column(name = "codigo_patrimonial", nullable = false, unique = true, length = 50)
    private String codigoPatrimonial;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "marca", length = 100)
    private String marca;

    @Column(name = "modelo", length = 100)
    private String modelo;

    @Column(name = "numero_serie", length = 100)
    private String numeroSerie;

    @Column(name = "fecha_adquisicion")
    private LocalDate fechaAdquisicion;

    @Column(name = "fecha_puesta_servicio")
    private LocalDate fechaPuestaServicio;

    @Column(name = "estado_operativo", nullable = false, length = 25)
    private String estadoOperativo = OPERATIVO;

    /** Estado de gestion: ACTIVO | INACTIVO. */
    @Column(name = "estado", nullable = false, length = 10)
    private String estado = ESTADO_ACTIVO;

    @Column(name = "valor_adquisicion", precision = 12, scale = 2)
    private BigDecimal valorAdquisicion;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    public boolean esActivo() {
        return ESTADO_ACTIVO.equals(estado);
    }
}
