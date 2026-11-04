package com.unh.mantenimiento.usuario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entidad Responsable: los usuarios del sistema.
 * El nombre de la tabla sigue el doc: 'responsable'.
 */
@Entity
@Table(name = "responsable")
@Getter
@Setter
@NoArgsConstructor
public class Responsable {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dni", length = 8)
    private String dni;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo", nullable = false, unique = true, length = 150)
    private String correo;

    /** Nunca se expone fuera del servidor. */
    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    public boolean esActivo() {
        return "ACTIVO".equals(estado);
    }

    public boolean estaBloqueado(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
