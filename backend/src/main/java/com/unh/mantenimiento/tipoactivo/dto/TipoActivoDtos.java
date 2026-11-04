package com.unh.mantenimiento.tipoactivo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs del catalogo de tipos de activo. */
public final class TipoActivoDtos {

    private TipoActivoDtos() {
    }

    public record TipoActivoResponse(
            Integer id,
            String nombre,
            String categoria,
            String descripcion,
            boolean requiereMantenimiento,
            Integer vidaUtilAnios
    ) {
    }

    public record TipoActivoRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres")
            String nombre,

            @NotBlank(message = "La categoria es obligatoria")
            @Pattern(
                    regexp = "EQUIPO|INFRAESTRUCTURA",
                    message = "La categoria debe ser EQUIPO o INFRAESTRUCTURA")
            String categoria,

            @Size(max = 255, message = "La descripcion no puede pasar de 255 caracteres")
            String descripcion,

            boolean requiereMantenimiento,

            @Min(value = 1, message = "La vida util debe ser al menos 1 ano")
            @Max(value = 100, message = "La vida util no puede pasar de 100 anos")
            Integer vidaUtilAnios
    ) {
    }
}
