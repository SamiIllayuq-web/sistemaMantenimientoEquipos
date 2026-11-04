package com.unh.mantenimiento.seguridad.service;

import java.time.LocalDateTime;

/** Usuario bloqueado temporalmente tras agotar los intentos fallidos. */
public class UsuarioBloqueadoException extends RuntimeException {

    private final LocalDateTime bloqueadoHasta;

    public UsuarioBloqueadoException(LocalDateTime bloqueadoHasta) {
        super("Usuario bloqueado temporalmente por intentos fallidos");
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
