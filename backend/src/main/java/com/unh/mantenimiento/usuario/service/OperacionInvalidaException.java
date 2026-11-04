package com.unh.mantenimiento.usuario.service;

/** Operacion invalida por violar una regla de negocio (422). */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
