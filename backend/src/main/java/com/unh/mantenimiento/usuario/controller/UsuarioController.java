package com.unh.mantenimiento.usuario.controller;

import com.unh.mantenimiento.usuario.dto.UsuarioDtos;
import com.unh.mantenimiento.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de usuarios y permisos.
 *
 * Cada endpoint exige la autoridad MOD_USUARIOS: el SERVIDOR valida.
 * Ocultar el menu en el frontend no es seguridad.
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasAuthority('MOD_USUARIOS')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioDtos.UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @GetMapping("/{dni}")
    public UsuarioDtos.UsuarioResponse buscar(@PathVariable String dni) {
        return usuarioService.buscar(dni);
    }

    @PostMapping
    public ResponseEntity<UsuarioDtos.UsuarioResponse> crear(
            @Valid @RequestBody UsuarioDtos.CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @PutMapping("/{dni}")
    public UsuarioDtos.UsuarioResponse actualizar(
            @PathVariable String dni,
            @Valid @RequestBody UsuarioDtos.ActualizarUsuarioRequest request) {
        return usuarioService.actualizar(dni, request);
    }

    /** Desactiva el usuario. No se borra (los registros relacionados se conservan). */
    @PutMapping("/{dni}/desactivar")
    public UsuarioDtos.UsuarioResponse desactivar(@PathVariable String dni) {
        return usuarioService.desactivar(dni);
    }
}
