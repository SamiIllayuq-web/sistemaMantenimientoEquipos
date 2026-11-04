package com.unh.mantenimiento.comun.excepcion;

import com.unh.mantenimiento.comun.dto.ErrorResponse;
import com.unh.mantenimiento.seguridad.service.CredencialesInvalidasException;
import com.unh.mantenimiento.seguridad.service.UsuarioBloqueadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo global de errores: formato uniforme (codigo HTTP + mensaje).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.de(401, "UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(UsuarioBloqueadoException.class)
    public ResponseEntity<ErrorResponse> usuarioBloqueado(UsuarioBloqueadoException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ErrorResponse.de(423, "LOCKED", e.getMessage()));
    }

    /** Errores de validacion de Bean Validation, campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "BAD_REQUEST",
                        "Los datos enviados no son validos", errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "BAD_REQUEST", "El cuerpo de la peticion no es valido"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> argumentoInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.de(404, "NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> duplicado(RecursoDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(409, "CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<ErrorResponse> operacionInvalida(OperacionInvalidaException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.de(422, "UNPROCESSABLE_ENTITY", e.getMessage()));
    }

    /**
     * Acceso denegado: el usuario esta autenticado pero no tiene la autoridad
     * que exige el endpoint (p.ej. MOD_UBICACIONES).
     * Debe declararse ANTES del handler generico de Exception, porque
     * AuthorizationDeniedException es una RuntimeException.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AuthorizationDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.de(403, "FORBIDDEN",
                        "No tienes permiso para realizar esta accion"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegadoClase(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.de(403, "FORBIDDEN",
                        "No tienes permiso para realizar esta accion"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorGenerico(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.de(500, "INTERNAL_SERVER_ERROR",
                        "Ocurrio un error inesperado en el servidor"));
    }
}
