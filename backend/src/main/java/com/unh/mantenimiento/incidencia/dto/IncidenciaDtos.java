package com.unh.mantenimiento.incidencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs del modulo de incidencias. */
public final class IncidenciaDtos {

    private IncidenciaDtos() {
    }

    /** Datos del activo (anidados en la respuesta). */
    public record ActivoResumen(
            Integer id,
            String codigoPatrimonial,
            String nombre
    ) {
    }

    /**
     * Respuesta de una incidencia. Trae el activo ANIDADO: incidencias y
     * activos son tablas separadas, pero el frontend los necesita juntos.
     */
    public record IncidenciaResponse(
            Integer id,
            LocalDateTime fechaDeteccion,
            String descripcion,
            String prioridad,
            String estado,
            LocalDateTime fechaCierre,
            String observaciones,
            ActivoResumen activo
    ) {
    }

    /**
     * Version ligera de una incidencia, para los desplegables (selectores).
     * Solo trae lo necesario para elegir: id, estado, descripcion y activo.
     */
    public record IncidenciaOpcion(
            Integer id,
            String estado,
            String descripcion,
            ActivoResumen activo
    ) {
    }

    /** Reporte de una incidencia. */
    public record CrearIncidenciaRequest(
            @NotNull(message = "Debe indicar el activo")
            Integer idActivo,

            @NotBlank(message = "La descripcion es obligatoria")
            @Size(max = 1000, message = "La descripcion no puede pasar de 1000 caracteres")
            String descripcion,

            @NotBlank(message = "La prioridad es obligatoria")
            @Pattern(
                    regexp = "ALTA|MEDIA|BAJA",
                    message = "La prioridad debe ser ALTA, MEDIA o BAJA")
            String prioridad,

            @PastOrPresent(message = "La fecha de deteccion no puede ser futura")
            LocalDateTime fechaDeteccion,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }

    /** Correccion de una incidencia: todos los campos son opcionales. */
    public record ActualizarIncidenciaRequest(
            @Size(max = 1000, message = "La descripcion no puede pasar de 1000 caracteres")
            String descripcion,

            @Pattern(
                    regexp = "ALTA|MEDIA|BAJA",
                    message = "La prioridad debe ser ALTA, MEDIA o BAJA")
            String prioridad,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }
}
