package com.unh.mantenimiento.comun.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato uniforme de error de la API: codigo HTTP + mensaje.
 * Todos los controllers devuelven esta estructura.
 */
public record ErrorResponse(
        int estado,
        String error,
        String mensaje,
        Map<String, String> erroresCampos,
        LocalDateTime timestamp
) {

    public static ErrorResponse de(int estado, String error, String mensaje) {
        return new ErrorResponse(estado, error, mensaje, null, LocalDateTime.now());
    }

    public static ErrorResponse de(int estado, String error, String mensaje,
                                    Map<String, String> erroresCampos) {
        return new ErrorResponse(estado, error, mensaje, erroresCampos, LocalDateTime.now());
    }
}
