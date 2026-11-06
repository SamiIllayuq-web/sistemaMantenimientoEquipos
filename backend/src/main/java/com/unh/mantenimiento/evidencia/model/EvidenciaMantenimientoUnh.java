package com.unh.mantenimiento.evidencia.model;

import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
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
 * Entidad EvidenciaMantenimientoUnh: una foto o un informe que respalda un
 * trabajo de mantenimiento.
 *
 * El archivo se guarda en disco con un nombre GENERADO (nunca el nombre que
 * envio el usuario); en la base solo queda la referencia. `nombreOriginal`
 * conserva el nombre real del archivo, solo para mostrarlo.
 *
 * La evidencia pertenece SIEMPRE a un mantenimiento existente.
 */
@Entity
@Table(name = "evidencia_mantenimiento_unh")
@Getter
@Setter
@NoArgsConstructor
public class EvidenciaMantenimientoUnh {

    /** El archivo es una foto. */
    public static final String FOTO = "FOTO";

    /** El archivo es un informe (documento). */
    public static final String INFORME = "INFORME";

    /** Otro tipo de archivo. */
    public static final String OTRO = "OTRO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evidencia")
    private Integer idEvidencia;

    /** El mantenimiento al que pertenece la evidencia. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_mantenimiento", nullable = false)
    private MantenimientoUnh mantenimiento;

    /** Tipo de archivo: FOTO, INFORME u OTRO. */
    @Column(name = "tipo_archivo", nullable = false, length = 15)
    private String tipoArchivo = FOTO;

    /** Nombre con el que se guardo el archivo en disco (generado). */
    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    /** Nombre original del archivo, tal como lo subio el usuario. */
    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    /** Descripcion de la evidencia (opcional). */
    @Column(name = "descripcion_evidencia", length = 500)
    private String descripcionEvidencia;

    @Column(name = "fecha_registro_evidencia", nullable = false)
    private LocalDateTime fechaRegistroEvidencia = LocalDateTime.now();
}
