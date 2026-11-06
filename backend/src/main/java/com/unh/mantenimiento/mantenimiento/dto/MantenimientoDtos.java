package com.unh.mantenimiento.mantenimiento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTOs del modulo de mantenimientos. */
public final class MantenimientoDtos {

    private MantenimientoDtos() {
    }

    /** Datos del activo (anidados en la respuesta). */
    public record ActivoResumen(
            Integer id,
            String codigoPatrimonial,
            String nombre
    ) {
    }

    /** Datos del responsable (anidados en la respuesta). */
    public record ResponsableResumen(
            String dni,
            String nombreCompleto
    ) {
    }

    /** Datos de la incidencia (anidada, solo en correctivos). */
    public record IncidenciaResumen(
            Integer id,
            String descripcion,
            String estado
    ) {
    }

    /** Respuesta de un mantenimiento. */
    public record MantenimientoResponse(
            Integer id,
            String tipoMantenimiento,
            String estadoMantenimiento,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            String diagnostico,
            String actividadRealizada,
            String resultado,
            BigDecimal horasInactividad,
            BigDecimal costoManoObra,
            LocalDate proximaFechaMantenimiento,
            String observaciones,
            ActivoResumen activo,
            ResponsableResumen responsable,
            IncidenciaResumen incidencia
    ) {
    }

    /**
     * Alta de mantenimiento CORRECTIVO: se crea desde una incidencia.
     * El responsable es el usuario con sesion: no se envia.
     */
    public record CrearCorrectivoRequest(
            @NotNull(message = "Debe indicar la incidencia")
            Integer idIncidencia,

            @PastOrPresent(message = "La fecha de inicio no puede ser futura")
            LocalDateTime fechaInicio,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }

    /**
     * Alta de mantenimiento PREVENTIVO: se registra a mano, sin incidencia.
     */
    public record CrearPreventivoRequest(
            @NotNull(message = "Debe indicar el activo")
            Integer idActivo,

            @PastOrPresent(message = "La fecha de inicio no puede ser futura")
            LocalDateTime fechaInicio,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }

    /** Actualizacion del trabajo en curso: diagnostico y actividad realizada. */
    public record ActualizarTrabajoRequest(
            @Size(max = 1000, message = "El diagnostico no puede pasar de 1000 caracteres")
            String diagnostico,

            @Size(max = 1000, message = "La actividad realizada no puede pasar de 1000 caracteres")
            String actividadRealizada,

            @Size(max = 500, message = "El resultado no puede pasar de 500 caracteres")
            String resultado,

            @DecimalMin(value = "0.00", message = "Las horas de inactividad no pueden ser negativas")
            BigDecimal horasInactividad,

            @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
            BigDecimal costoManoObra,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }

    /** Cierre del trabajo: fecha fin y datos finales. */
    public record FinalizarRequest(
            @NotNull(message = "Debe indicar la fecha de fin")
            @PastOrPresent(message = "La fecha de fin no puede ser futura")
            LocalDateTime fechaFin,

            @Size(max = 1000, message = "El diagnostico no puede pasar de 1000 caracteres")
            String diagnostico,

            @Size(max = 1000, message = "La actividad realizada no puede pasar de 1000 caracteres")
            String actividadRealizada,

            @Size(max = 500, message = "El resultado no puede pasar de 500 caracteres")
            String resultado,

            @DecimalMin(value = "0.00", message = "Las horas de inactividad no pueden ser negativas")
            BigDecimal horasInactividad,

            @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
            BigDecimal costoManoObra,

            @Pattern(
                    regexp = "\\d{4}-\\d{2}-\\d{2}",
                    message = "La proxima fecha de mantenimiento debe tener formato AAAA-MM-DD")
            String proximaFechaMantenimiento,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }
}
