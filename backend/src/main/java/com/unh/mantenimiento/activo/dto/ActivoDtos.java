package com.unh.mantenimiento.activo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs del modulo de activos. */
public final class ActivoDtos {

    private ActivoDtos() {
    }

    /** Datos de un tipo de activo (anidado en la respuesta). */
    public record TipoActivoResumen(
            Integer id,
            String nombre,
            String categoria
    ) {
    }

    /** Datos de una ubicacion (anidada en la respuesta). */
    public record UbicacionResumen(
            Integer id,
            String nombre,
            String tipoUbicacion
    ) {
    }

    /**
     * Version ligera de un activo, para los desplegables (selectores).
     * No trae tipo ni ubicacion: solo lo necesario para elegir.
     */
    public record ActivoOpcion(
            Integer id,
            String codigoPatrimonial,
            String nombre
    ) {
    }

    /**
     * Respuesta de un activo. Trae tipo y ubicacion ANIDADOS: activos y
     * ubicaciones son tablas separadas, pero el frontend los necesita juntos.
     */
    public record ActivoResponse(
            Integer id,
            String codigoPatrimonial,
            String nombre,
            String marca,
            String modelo,
            String numeroSerie,
            LocalDate fechaAdquisicion,
            LocalDate fechaPuestaServicio,
            String estadoOperativo,
            String estado,
            BigDecimal valorAdquisicion,
            String observaciones,
            TipoActivoResumen tipoActivo,
            UbicacionResumen ubicacion
    ) {
    }

    /** Alta de activo. */
    public record CrearActivoRequest(
            @NotBlank(message = "El codigo patrimonial es obligatorio")
            @Size(max = 50, message = "El codigo patrimonial no puede pasar de 50 caracteres")
            String codigoPatrimonial,

            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
            String nombre,

            @NotNull(message = "Debe indicar el tipo de activo")
            Integer idTipoActivo,

            @NotNull(message = "Debe indicar la ubicacion")
            Integer idUbicacion,

            @Size(max = 100, message = "La marca no puede pasar de 100 caracteres")
            String marca,

            @Size(max = 100, message = "El modelo no puede pasar de 100 caracteres")
            String modelo,

            @Size(max = 100, message = "El numero de serie no puede pasar de 100 caracteres")
            String numeroSerie,

            @PastOrPresent(message = "La fecha de adquisicion no puede ser futura")
            LocalDate fechaAdquisicion,

            @PastOrPresent(message = "La fecha de puesta en servicio no puede ser futura")
            LocalDate fechaPuestaServicio,

            @Pattern(
                    regexp = "OPERATIVO|EN_MANTENIMIENTO|FUERA_DE_SERVICIO",
                    message = "El estado operativo debe ser OPERATIVO, EN_MANTENIMIENTO o FUERA_DE_SERVICIO")
            String estadoOperativo,

            @DecimalMin(value = "0.00", message = "El valor de adquisicion no puede ser negativo")
            BigDecimal valorAdquisicion,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }

    /** Actualizacion de activo: todos los campos son opcionales. */
    public record ActualizarActivoRequest(
            @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
            String nombre,

            Integer idTipoActivo,

            Integer idUbicacion,

            @Size(max = 100, message = "La marca no puede pasar de 100 caracteres")
            String marca,

            @Size(max = 100, message = "El modelo no puede pasar de 100 caracteres")
            String modelo,

            @Size(max = 100, message = "El numero de serie no puede pasar de 100 caracteres")
            String numeroSerie,

            @PastOrPresent(message = "La fecha de adquisicion no puede ser futura")
            LocalDate fechaAdquisicion,

            @PastOrPresent(message = "La fecha de puesta en servicio no puede ser futura")
            LocalDate fechaPuestaServicio,

            @Pattern(
                    regexp = "OPERATIVO|EN_MANTENIMIENTO|FUERA_DE_SERVICIO",
                    message = "El estado operativo debe ser OPERATIVO, EN_MANTENIMIENTO o FUERA_DE_SERVICIO")
            String estadoOperativo,

            @DecimalMin(value = "0.00", message = "El valor de adquisicion no puede ser negativo")
            BigDecimal valorAdquisicion,

            @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
            String observaciones
    ) {
    }
}
