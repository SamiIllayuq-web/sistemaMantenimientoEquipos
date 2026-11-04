package com.unh.mantenimiento.ubicacion.controller;

import com.unh.mantenimiento.ubicacion.dto.UbicacionDtos;
import com.unh.mantenimiento.ubicacion.service.UbicacionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de ubicaciones (catalogo).
 *
 * LECTURA: abierta a cualquier usuario con sesion (cualquier rol). Se usa
 * para llenar desplegables en otras pantallas.
 * ESCRITURA: exige la autoridad MOD_UBICACIONES. El servidor valida.
 */
@RestController
@RequestMapping("/api/ubicaciones")
public class UbicacionController {

    private final UbicacionService ubicacionService;

    public UbicacionController(UbicacionService ubicacionService) {
        this.ubicacionService = ubicacionService;
    }

    @GetMapping
    public List<UbicacionDtos.UbicacionResponse> listar() {
        return ubicacionService.listar();
    }

    @GetMapping("/{id}")
    public UbicacionDtos.UbicacionResponse buscar(@PathVariable Integer id) {
        return ubicacionService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MOD_UBICACIONES')")
    public ResponseEntity<UbicacionDtos.UbicacionResponse> crear(
            @Valid @RequestBody UbicacionDtos.UbicacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ubicacionService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_UBICACIONES')")
    public UbicacionDtos.UbicacionResponse actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UbicacionDtos.UbicacionRequest request) {
        return ubicacionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_UBICACIONES')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        ubicacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
