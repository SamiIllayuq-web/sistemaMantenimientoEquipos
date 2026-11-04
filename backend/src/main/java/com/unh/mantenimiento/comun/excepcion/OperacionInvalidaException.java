package com.unh.mantenimiento.comun.excepcion;

/** Operacion invalida por violar una regla de negocio (422). */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
