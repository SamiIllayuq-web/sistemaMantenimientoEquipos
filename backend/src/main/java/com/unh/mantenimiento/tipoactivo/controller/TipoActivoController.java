package com.unh.mantenimiento.tipoactivo.controller;

import com.unh.mantenimiento.tipoactivo.dto.TipoActivoDtos;
import com.unh.mantenimiento.tipoactivo.service.TipoActivoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de tipos de activo (catalogo).
 *
 * LECTURA: abierta a cualquier usuario con sesion (cualquier rol).
 * ESCRITURA: exige la autoridad MOD_TIPOS_ACTIVO. El servidor valida.
 */
@RestController
@RequestMapping("/api/tipos-activo")
public class TipoActivoController {

    private final TipoActivoService tipoActivoService;

    public TipoActivoController(TipoActivoService tipoActivoService) {
        this.tipoActivoService = tipoActivoService;
    }

    @GetMapping
    public List<TipoActivoDtos.TipoActivoResponse> listar(
            @RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return tipoActivoService.listarPorCategoria(categoria);
        }
        return tipoActivoService.listar();
    }

    @GetMapping("/{id}")
    public TipoActivoDtos.TipoActivoResponse buscar(@PathVariable Integer id) {
        return tipoActivoService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MOD_TIPOS_ACTIVO')")
    public ResponseEntity<TipoActivoDtos.TipoActivoResponse> crear(
            @Valid @RequestBody TipoActivoDtos.TipoActivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipoActivoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_TIPOS_ACTIVO')")
    public TipoActivoDtos.TipoActivoResponse actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody TipoActivoDtos.TipoActivoRequest request) {
        return tipoActivoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_TIPOS_ACTIVO')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        tipoActivoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
