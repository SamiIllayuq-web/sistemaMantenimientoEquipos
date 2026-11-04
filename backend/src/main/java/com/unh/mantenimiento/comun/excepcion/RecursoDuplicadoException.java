package com.unh.mantenimiento.comun.excepcion;

/** Recurso duplicado (409): violate una restriccion de unicidad. */
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
