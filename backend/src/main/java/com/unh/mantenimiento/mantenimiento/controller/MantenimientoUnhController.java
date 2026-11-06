package com.unh.mantenimiento.mantenimiento.controller;

import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.mantenimiento.dto.MantenimientoDtos;
import com.unh.mantenimiento.mantenimiento.service.MantenimientoUnhService;
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
 * Controlador de mantenimientos.
 *
 * LECTURA: requiere sesion (cualquier rol con sesion puede consultar).
 * ESCRITURA: exige la autoridad MOD_MANTENIMIENTOS. El servidor valida.
 */
@RestController
@RequestMapping("/api/mantenimientos")
public class MantenimientoUnhController {

    private final MantenimientoUnhService mantenimientoService;

    public MantenimientoUnhController(MantenimientoUnhService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    /**
     * Listado paginado de mantenimientos.
     *
     * @param estado    EN_PROCESO, FINALIZADO o todos (opcional)
     * @param idActivo  filtra por activo (opcional)
     * @param tipo      CORRECTIVO, PREVENTIVO o todos (opcional)
     * @param pagina    numero de pagina, base 1 (por defecto 1)
     * @param tamano    registros por pagina (por defecto 20, maximo 100)
     */
    @GetMapping
    public PaginaResponse<MantenimientoDtos.MantenimientoResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idActivo,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return mantenimientoService.listarPaginado(estado, idActivo, tipo, pagina, tamano);
    }

    /** Detalle: trae activo, responsable e incidencia anidados. */
    @GetMapping("/{id}")
    public MantenimientoDtos.MantenimientoResponse buscar(@PathVariable Integer id) {
        return mantenimientoService.buscar(id);
    }

    /**
     * Historial de un activo: sus mantenimientos.
     * Es lo que consume el boton "Historial" de la pantalla de Activos.
     */
    @GetMapping("/activo/{idActivo}")
    public List<MantenimientoDtos.MantenimientoResponse> historialDeActivo(
            @PathVariable Integer idActivo) {
        return mantenimientoService.historialDeActivo(idActivo);
    }

    /** Crea un mantenimiento correctivo desde una incidencia. */
    @PostMapping("/correctivo")
    @PreAuthorize("hasAuthority('MOD_MANTENIMIENTOS')")
    public ResponseEntity<MantenimientoDtos.MantenimientoResponse> crearCorrectivo(
            @Valid @RequestBody MantenimientoDtos.CrearCorrectivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mantenimientoService.crearCorrectivo(request));
    }

    /** Crea un mantenimiento preventivo (sin incidencia). */
    @PostMapping("/preventivo")
    @PreAuthorize("hasAuthority('MOD_MANTENIMIENTOS')")
    public ResponseEntity<MantenimientoDtos.MantenimientoResponse> crearPreventivo(
            @Valid @RequestBody MantenimientoDtos.CrearPreventivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mantenimientoService.crearPreventivo(request));
    }

    /** Actualiza el trabajo en curso. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_MANTENIMIENTOS')")
    public MantenimientoDtos.MantenimientoResponse actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody MantenimientoDtos.ActualizarTrabajoRequest request) {
        return mantenimientoService.actualizar(id, request);
    }

    /** Finaliza el trabajo: cierra la incidencia y devuelve el activo a OPERATIVO. */
    @PutMapping("/{id}/finalizar")
    @PreAuthorize("hasAuthority('MOD_MANTENIMIENTOS')")
    public MantenimientoDtos.MantenimientoResponse finalizar(
            @PathVariable Integer id,
            @Valid @RequestBody MantenimientoDtos.FinalizarRequest request) {
        return mantenimientoService.finalizar(id, request);
    }
}
