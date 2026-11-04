package com.unh.mantenimiento.tipoactivo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad TipoActivo: catalogo de tipos de activo.
 * La categoria distingue EQUIPO de INFRAESTRUCTURA (CHECK en la BD).
 */
@Entity
@Table(name = "tipo_activo")
@Getter
@Setter
@NoArgsConstructor
public class TipoActivo {

    /** Valores permitidos para la categoria (mismo CHECK que en la BD). */
    public static final String CATEGORIA_EQUIPO = "EQUIPO";
    public static final String CATEGORIA_INFRAESTRUCTURA = "INFRAESTRUCTURA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_activo")
    private Integer idTipoActivo;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "categoria", nullable = false, length = 20)
    private String categoria;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "requiere_mantenimiento", nullable = false)
    private boolean requiereMantenimiento = true;

    @Column(name = "vida_util_anios")
    private Integer vidaUtilAnios;
}
