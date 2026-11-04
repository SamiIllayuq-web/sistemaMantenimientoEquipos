package com.unh.mantenimiento.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** DTOs de entrada y salida del modulo de usuarios. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** Datos publicos de un usuario. Nunca incluye clave_hash. */
    public record UsuarioResponse(
            String dni,
            String nombres,
            String apellidos,
            String correo,
            String telefono,
            String estado,
            List<String> modulos
    ) {
        public String nombreCompleto() {
            return nombres + " " + apellidos;
        }
    }

    /** Alta de usuario. */
    public record CrearUsuarioRequest(
            @NotBlank(message = "El DNI es obligatorio")
            @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 digitos")
            String dni,

            @NotBlank(message = "Los nombres son obligatorios")
            String nombres,

            @NotBlank(message = "Los apellidos son obligatorios")
            String apellidos,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido")
            String correo,

            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
            String clave,

            String telefono,

            @NotEmpty(message = "Debe asignar al menos un modulo")
            List<String> modulos
    ) {
    }

    /** Actualizacion de usuario. Todos los campos son opcionales. */
    public record ActualizarUsuarioRequest(
            String nombres,
            String apellidos,
            @Email(message = "El correo no tiene un formato valido")
            String correo,
            String telefono,
            String estado,
            List<String> modulos
    ) {
    }
}
