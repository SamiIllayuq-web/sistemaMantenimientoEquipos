package com.unh.mantenimiento.seguridad.controller;

import com.unh.mantenimiento.seguridad.dto.AuthDtos;
import com.unh.mantenimiento.seguridad.model.UsuarioAutenticado;
import com.unh.mantenimiento.seguridad.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de autenticacion. Fachada fina: recibe HTTP y responde DTOs.
 * Toda la logica esta en AuthService.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(authService.iniciarSesion(request.correo(), request.clave()));
    }

    /** Perfil del usuario autenticado + sus modulos (para construir el menu). */
    @GetMapping("/me")
    public ResponseEntity<AuthDtos.MeResponse> me(
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        AuthDtos.UsuarioResponse datos = new AuthDtos.UsuarioResponse(
                usuario.getDni(),
                usuario.getNombreCompleto(),
                "",
                usuario.getUsername(),
                null,
                usuario.getEstado()
        );

        List<String> modulos = usuario.getAutoridades().stream()
                .map(a -> a.replaceFirst("^MOD_", ""))
                .toList();

        return ResponseEntity.ok(new AuthDtos.MeResponse(datos, modulos));
    }
}
