package com.unh.mantenimiento.seguridad.service;

/**
 * Credenciales invalidas: usuario inexistente, clave incorrecta o usuario
 * inactivo. El mismo mensaje para todos los casos (no revela si el usuario
 * existe).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Correo o contrasena incorrectos");
    }
}
