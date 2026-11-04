package com.unh.mantenimiento.incidencia.service;

import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.repository.ActivoUnhRepository;
import com.unh.mantenimiento.comun.Paginacion;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.incidencia.dto.IncidenciaDtos;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.incidencia.repository.IncidenciaUnhRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de incidencias. Deep module: interfaz pequena, logica densa adentro.
 *
 * Reglas que vive aqui:
 * - Toda incidencia pertenece a un activo existente.
 * - La incidencia nace ABIERTA: el paso a EN_ATENCION y CERRADA lo hace el
 *   modulo de mantenimientos (I5), no el usuario.
 * - Solo se puede corregir una incidencia mientras esta ABIERTA.
 * - Un activo desactivado (INACTIVO) no recibe incidencias nuevas.
 */
@Service
public class IncidenciaUnhService {

    private final IncidenciaUnhRepository incidenciaRepository;
    private final ActivoUnhRepository activoRepository;

    public IncidenciaUnhService(IncidenciaUnhRepository incidenciaRepository,
                                ActivoUnhRepository activoRepository) {
        this.incidenciaRepository = incidenciaRepository;
        this.activoRepository = activoRepository;
    }

    /** Listado con filtro opcional por estado y por activo. */
    @Transactional(readOnly = true)
    public List<IncidenciaDtos.IncidenciaResponse> listar(String estado, Integer idActivo) {
        String estadoLimpio = (estado == null || estado.isBlank()) ? null : estado.trim();
        Integer activoLimpio = (idActivo == null) ? null : idActivo;

        return incidenciaRepository.buscar(estadoLimpio, activoLimpio)
                .stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Listado paginado con los mismos filtros. La pagina es BASE 1: la
     * primera pagina es la 1. Se ordena por fecha de deteccion descendente
     * (la mas reciente primero).
     */
    @Transactional(readOnly = true)
    public PaginaResponse<IncidenciaDtos.IncidenciaResponse> listarPaginado(
            String estado, Integer idActivo, int pagina, int tamano) {
        String estadoLimpio = (estado == null || estado.isBlank()) ? null : estado.trim();
        Integer activoLimpio = (idActivo == null) ? null : idActivo;

        int paginaSegura = Paginacion.normalizarPagina(pagina);
        int tamanoSeguro = Paginacion.normalizarTamano(tamano);
        Pageable pageable = Paginacion.aPageable(
                paginaSegura,
                tamanoSeguro,
                Sort.by(Sort.Order.desc("fechaDeteccion"), Sort.Order.desc("idIncidencia")));

        Page<IncidenciaUnh> resultado =
                incidenciaRepository.buscarPaginado(estadoLimpio, activoLimpio, pageable);

        return PaginaResponse.de(
                resultado.getContent().stream().map(this::aResponse).toList(),
                paginaSegura,
                tamanoSeguro,
                resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public IncidenciaDtos.IncidenciaResponse buscar(Integer id) {
        return aResponse(obtener(id));
    }

    /** Reporta una incidencia. Nace ABIERTA. */
    @Transactional
    public IncidenciaDtos.IncidenciaResponse crear(IncidenciaDtos.CrearIncidenciaRequest request) {
        ActivoUnh activo = obtenerActivo(request.idActivo());

        if (!activo.esActivo()) {
            throw new OperacionInvalidaException(
                    "El activo " + activo.getCodigoPatrimonial()
                            + " esta desactivado: no admite incidencias nuevas");
        }

        IncidenciaUnh incidencia = new IncidenciaUnh();
        incidencia.setActivo(activo);
        incidencia.setDescripcion(request.descripcion().trim());
        incidencia.setPrioridad(request.prioridad());
        incidencia.setEstado(IncidenciaUnh.ABIERTA);
        incidencia.setFechaDeteccion(
                request.fechaDeteccion() == null ? LocalDateTime.now() : request.fechaDeteccion());
        incidencia.setObservaciones(request.observaciones());

        incidenciaRepository.save(incidencia);

        return aResponse(incidencia);
    }

    /**
     * Corrige una incidencia. Solo se permite mientras esta ABIERTA: una vez
     * en proceso o cerrada, el historial no se altera.
     */
    @Transactional
    public IncidenciaDtos.IncidenciaResponse actualizar(
            Integer id, IncidenciaDtos.ActualizarIncidenciaRequest request) {
        IncidenciaUnh incidencia = obtener(id);

        if (!incidencia.esAbierta()) {
            throw new OperacionInvalidaException(
                    "Solo se puede corregir una incidencia ABIERTA. "
                            + "Esta esta en estado " + incidencia.getEstado());
        }

        if (request.descripcion() != null) {
            incidencia.setDescripcion(request.descripcion().trim());
        }
        if (request.prioridad() != null && !request.prioridad().isBlank()) {
            incidencia.setPrioridad(request.prioridad());
        }
        if (request.observaciones() != null) {
            incidencia.setObservaciones(request.observaciones());
        }

        incidenciaRepository.save(incidencia);

        return aResponse(incidencia);
    }

    private IncidenciaUnh obtener(Integer id) {
        return incidenciaRepository.buscarDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una incidencia con ID " + id));
    }

    private ActivoUnh obtenerActivo(Integer id) {
        return activoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un activo con ID " + id));
    }

    private IncidenciaDtos.IncidenciaResponse aResponse(IncidenciaUnh incidencia) {
        ActivoUnh activo = incidencia.getActivo();

        return new IncidenciaDtos.IncidenciaResponse(
                incidencia.getIdIncidencia(),
                incidencia.getFechaDeteccion(),
                incidencia.getDescripcion(),
                incidencia.getPrioridad(),
                incidencia.getEstado(),
                incidencia.getFechaCierre(),
                incidencia.getObservaciones(),
                new IncidenciaDtos.ActivoResumen(
                        activo.getIdActivo(),
                        activo.getCodigoPatrimonial(),
                        activo.getNombre()
                )
        );
    }
}
