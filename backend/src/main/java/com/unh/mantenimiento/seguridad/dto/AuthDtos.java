package com.unh.mantenimiento.seguridad.dto;

import com.unh.mantenimiento.usuario.model.ResponsableUnh;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** DTOs de entrada y salida del modulo de seguridad. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Entrada del login. */
    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido")
            String correo,

            @NotBlank(message = "La contrasena es obligatoria")
            String clave
    ) {
    }

    /** Salida del login: token + datos del usuario + modulos. */
    public record LoginResponse(
            String token,
            String tipoToken,
            long expiraEnSegundos,
            UsuarioResponse usuario,
            List<String> modulos
    ) {
        public static LoginResponse de(String token, ResponsableUnh responsable,
                                       List<String> modulos, long expiraEnSegundos) {
            return new LoginResponse(
                    token,
                    "Bearer",
                    expiraEnSegundos,
                    new UsuarioResponse(
                            responsable.getDni(),
                            responsable.getNombres(),
                            responsable.getApellidos(),
                            responsable.getCorreo(),
                            responsable.getTelefono(),
                            responsable.getEstado()
                    ),
                    modulos
            );
        }
    }

    /** Datos publicos de un usuario. Nunca incluye clave_hash. */
    public record UsuarioResponse(
            String dni,
            String nombres,
            String apellidos,
            String correo,
            String telefono,
            String estado
    ) {
        public String nombreCompleto() {
            return nombres + " " + apellidos;
        }
    }

    /** Respuesta de /api/auth/me. */
    public record MeResponse(
            UsuarioResponse usuario,
            List<String> modulos
    ) {
    }
}
