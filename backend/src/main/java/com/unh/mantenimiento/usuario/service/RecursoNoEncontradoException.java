package com.unh.mantenimiento.usuario.service;

/** Recurso no encontrado (404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
