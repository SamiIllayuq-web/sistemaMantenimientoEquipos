package com.unh.mantenimiento.ubicacion.model;

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
 * Entidad Ubicacion: catalogo de lugares de la residencia.
 * 'tipo_ubicacion' es texto libre (HABITACION, COMEDOR, ...) a proposito:
 * la institucion puede agregar nuevos tipos sin cambiar el esquema.
 */
@Entity
@Table(name = "ubicacion")
@Getter
@Setter
@NoArgsConstructor
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ubicacion")
    private Integer idUbicacion;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "tipo_ubicacion", nullable = false, length = 50)
    private String tipoUbicacion;

    @Column(name = "pabellon", length = 50)
    private String pabellon;

    @Column(name = "piso", length = 20)
    private String piso;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}
