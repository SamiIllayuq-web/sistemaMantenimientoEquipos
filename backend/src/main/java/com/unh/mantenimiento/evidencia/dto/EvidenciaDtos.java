package com.unh.mantenimiento.evidencia.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs del modulo de evidencias. */
public final class EvidenciaDtos {

    private EvidenciaDtos() {
    }

    /**
     * Respuesta de una evidencia.
     *
     * `nombreArchivo` es el nombre GENERADO en disco; nunca se expone la ruta
     * completa del servidor. La descarga se hace por un endpoint que exige
     * sesion y el modulo EVIDENCIAS.
     */
    public record EvidenciaResponse(
            Integer id,
            Integer idMantenimiento,
            String tipoArchivo,
            String nombreArchivo,
            String nombreOriginal,
            String descripcion,
            LocalDateTime fechaRegistro,
            Long tamanoBytes
    ) {
    }

    /**
     * Datos de la evidencia que accompanyan al archivo.
     *
     * El archivo viaja en el multipart (campo `archivo`), no aqui: en el JSON
     * solo van los metadatos.
     */
    public record RegistrarEvidenciaRequest(
            @Size(max = 500, message = "La descripcion no puede pasar de 500 caracteres")
            String descripcion,

            @Pattern(
                    regexp = "FOTO|INFORME|OTRO",
                    message = "El tipo debe ser FOTO, INFORME u OTRO")
            String tipoArchivo
    ) {
    }
}
