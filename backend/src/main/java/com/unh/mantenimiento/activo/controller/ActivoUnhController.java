package com.unh.mantenimiento.activo.controller;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de activos.
 *
 * LECTURA: requiere sesion (cualquier rol con sesion puede consultar el
 * inventario).
 * ESCRITURA: exige la autoridad MOD_ACTIVOS. El servidor valida.
 */
@RestController
@RequestMapping("/api/activos")
public class ActivoUnhController {

    private final ActivoUnhService activoService;

    public ActivoUnhController(ActivoUnhService activoService) {
        this.activoService = activoService;
    }

    /**
     * Listado paginado de activos.
     *
     * @param texto  busqueda por nombre o codigo patrimonial (opcional)
     * @param estado ACTIVO (por defecto), INACTIVO o todos (opcional)
     * @param pagina numero de pagina, base 1 (por defecto 1)
     * @param tamano registros por pagina (por defecto 20, maximo 100)
     */
    @GetMapping
    public PaginaResponse<ActivoDtos.ActivoResponse> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return activoService.listarPaginado(texto, estado, pagina, tamano);
    }

    /**
     * Lista ligera para los desplegables: solo id, codigo y nombre.
     * Sin paginacion a proposito: un selector debe ofrecer todas las opciones.
     */
    @GetMapping("/opciones")
    public List<ActivoDtos.ActivoOpcion> opciones() {
        return activoService.listarOpciones();
    }

    /** Detalle: trae tipo y ubicacion anidados. */
    @GetMapping("/{id}")
    public ActivoDtos.ActivoResponse buscar(@PathVariable Integer id) {
        return activoService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MOD_ACTIVOS')")
    public ResponseEntity<ActivoDtos.ActivoResponse> crear(
            @Valid @RequestBody ActivoDtos.CrearActivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_ACTIVOS')")
    public ActivoDtos.ActivoResponse actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ActivoDtos.ActualizarActivoRequest request) {
        return activoService.actualizar(id, request);
    }

    /** Desactiva el activo. NO se borra: el inventario se conserva. */
    @PutMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('MOD_ACTIVOS')")
    public ActivoDtos.ActivoResponse desactivar(@PathVariable Integer id) {
        return activoService.desactivar(id);
    }

    /** Reactiva un activo desactivado. */
    @PutMapping("/{id}/reactivar")
    @PreAuthorize("hasAuthority('MOD_ACTIVOS')")
    public ActivoDtos.ActivoResponse reactivar(@PathVariable Integer id) {
        return activoService.reactivar(id);
    }
}
