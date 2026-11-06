package com.unh.mantenimiento.incidencia.controller;

import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.incidencia.dto.IncidenciaDtos;
import com.unh.mantenimiento.incidencia.service.IncidenciaUnhService;
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
 * Controlador de incidencias.
 *
 * LECTURA: requiere sesion (cualquier rol con sesion puede consultar).
 * ESCRITURA: exige la autoridad MOD_INCIDENCIAS. El servidor valida.
 *
 * No hay endpoint para cambiar el estado: la incidencia nace ABIERTA y la
 * transicion a EN_ATENCION / CERRADA la hace el modulo de mantenimientos.
 */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaUnhController {

    private final IncidenciaUnhService incidenciaService;

    public IncidenciaUnhController(IncidenciaUnhService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    /**
     * Listado paginado de incidencias.
     *
     * @param estado    ABIERTA, EN_ATENCION, CERRADA o todos (opcional)
     * @param idActivo  filtra por activo (opcional)
     * @param pagina    numero de pagina, base 1 (por defecto 1)
     * @param tamano    registros por pagina (por defecto 20, maximo 100)
     */
    @GetMapping
    public PaginaResponse<IncidenciaDtos.IncidenciaResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idActivo,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return incidenciaService.listarPaginado(estado, idActivo, pagina, tamano);
    }

    /**
     * Incidencias ABIERTAS para los desplegables (selectores).
     * Sin paginar: un selector debe ofrecer todas las opciones.
     */
    @GetMapping("/opciones")
    public List<IncidenciaDtos.IncidenciaOpcion> opciones() {
        return incidenciaService.listarOpciones();
    }

    /** Detalle: trae el activo anidado. */
    @GetMapping("/{id}")
    public IncidenciaDtos.IncidenciaResponse buscar(@PathVariable Integer id) {
        return incidenciaService.buscar(id);
    }

    /** Reporta una incidencia. Nace ABIERTA. */
    @PostMapping
    @PreAuthorize("hasAuthority('MOD_INCIDENCIAS')")
    public ResponseEntity<IncidenciaDtos.IncidenciaResponse> crear(
            @Valid @RequestBody IncidenciaDtos.CrearIncidenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidenciaService.crear(request));
    }

    /** Corrige una incidencia ABIERTA. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_INCIDENCIAS')")
    public IncidenciaDtos.IncidenciaResponse actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody IncidenciaDtos.ActualizarIncidenciaRequest request) {
        return incidenciaService.actualizar(id, request);
    }
}
