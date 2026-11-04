package com.unh.mantenimiento.usuario.service;

/** Recurso duplicado (409): DNI o correo ya registrados. */
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
