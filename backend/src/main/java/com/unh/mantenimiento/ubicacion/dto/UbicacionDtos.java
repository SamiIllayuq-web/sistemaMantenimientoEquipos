package com.unh.mantenimiento.ubicacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs del catalogo de ubicaciones. */
public final class UbicacionDtos {

    private UbicacionDtos() {
    }

    public record UbicacionResponse(
            Integer id,
            String nombre,
            String tipoUbicacion,
            String pabellon,
            String piso,
            String descripcion
    ) {
    }

    public record UbicacionRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres")
            String nombre,

            @NotBlank(message = "El tipo de ubicacion es obligatorio")
            @Size(max = 50, message = "El tipo no puede pasar de 50 caracteres")
            String tipoUbicacion,

            @Size(max = 50, message = "El pabellon no puede pasar de 50 caracteres")
            String pabellon,

            @Size(max = 20, message = "El piso no puede pasar de 20 caracteres")
            String piso,

            @Size(max = 255, message = "La descripcion no puede pasar de 255 caracteres")
            String descripcion
    ) {
    }
}
