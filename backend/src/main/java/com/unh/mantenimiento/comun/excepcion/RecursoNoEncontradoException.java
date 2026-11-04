package com.unh.mantenimiento.comun.excepcion;

/** Recurso no encontrado (404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
