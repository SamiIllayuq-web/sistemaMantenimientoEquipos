package com.unh.mantenimiento.tipoactivo.service;

import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.tipoactivo.dto.TipoActivoDtos;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de tipos de activo (catalogo). Deep module: interfaz pequena,
 * logica densa adentro (unicidad, categoria valida, guard de borrado).
 */
@Service
public class TipoActivoService {

    private final TipoActivoUnhRepository tipoActivoRepository;
    private final EntityManager entityManager;

    public TipoActivoService(TipoActivoUnhRepository tipoActivoRepository, EntityManager entityManager) {
        this.tipoActivoRepository = tipoActivoRepository;
        this.entityManager = entityManager;
    }

    /** Lectura: abierta a cualquier usuario con sesion. */
    @Transactional(readOnly = true)
    public List<TipoActivoDtos.TipoActivoResponse> listar() {
        return tipoActivoRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::aResponse)
                .toList();
    }

    /** Listado por categoria (EQUIPO / INFRAESTRUCTURA). */
    @Transactional(readOnly = true)
    public List<TipoActivoDtos.TipoActivoResponse> listarPorCategoria(String categoria) {
        return tipoActivoRepository.findAllByCategoriaOrderByNombreAsc(categoria)
                .stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TipoActivoDtos.TipoActivoResponse buscar(Integer id) {
        return aResponse(obtener(id));
    }

    /** Escritura: exige el modulo TIPOS_ACTIVO (lo valida el servidor). */
    @Transactional
    public TipoActivoDtos.TipoActivoResponse crear(TipoActivoDtos.TipoActivoRequest request) {
        if (tipoActivoRepository.existsByNombre(request.nombre().trim())) {
            throw new RecursoDuplicadoException("Ya existe un tipo de activo con ese nombre");
        }

        TipoActivoUnh tipoActivo = new TipoActivoUnh();
        tipoActivo.setNombre(request.nombre().trim());
        tipoActivo.setCategoria(request.categoria());
        tipoActivo.setDescripcion(request.descripcion());
        // Si el campo no vino en el JSON, se aplica el valor por defecto:
        // un tipo de activo nuevo requiere mantenimiento.
        tipoActivo.setRequiereMantenimiento(request.requiereMantenimiento() == null
                || request.requiereMantenimiento());
        tipoActivo.setVidaUtilAnios(request.vidaUtilAnios());
        tipoActivoRepository.save(tipoActivo);

        return aResponse(tipoActivo);
    }

    @Transactional
    public TipoActivoDtos.TipoActivoResponse actualizar(
            Integer id, TipoActivoDtos.TipoActivoRequest request) {
        TipoActivoUnh tipoActivo = obtener(id);

        if (!request.nombre().trim().equals(tipoActivo.getNombre())
                && tipoActivoRepository.existsByNombre(request.nombre().trim())) {
            throw new RecursoDuplicadoException("Ya existe un tipo de activo con ese nombre");
        }

        tipoActivo.setNombre(request.nombre().trim());
        tipoActivo.setCategoria(request.categoria());
        tipoActivo.setDescripcion(request.descripcion());
        // Si el campo no vino en el JSON, se conserva el valor actual.
        if (request.requiereMantenimiento() != null) {
            tipoActivo.setRequiereMantenimiento(request.requiereMantenimiento());
        }
        tipoActivo.setVidaUtilAnios(request.vidaUtilAnios());
        tipoActivoRepository.save(tipoActivo);

        return aResponse(tipoActivo);
    }

    /**
     * Elimina un tipo de activo. Si hay activos que lo referencian, NO se
     * borra: se rechaza con un mensaje claro.
     */
    @Transactional
    public void eliminar(Integer id) {
        TipoActivoUnh tipoActivo = obtener(id);

        long activosVinculados = contarActivosConTipo(id);
        if (activosVinculados > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar: hay " + activosVinculados
                            + " activo(s) que usan este tipo de activo");
        }

        tipoActivoRepository.delete(tipoActivo);
    }

    private long contarActivosConTipo(Integer idTipoActivo) {
        Object resultado = entityManager
                .createNativeQuery("SELECT COUNT(*) FROM activo_unh WHERE id_tipo_activo = :id")
                .setParameter("id", idTipoActivo)
                .getSingleResult();
        return ((Number) resultado).longValue();
    }

    private TipoActivoUnh obtener(Integer id) {
        return tipoActivoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un tipo de activo con ID " + id));
    }

    private TipoActivoDtos.TipoActivoResponse aResponse(TipoActivoUnh tipoActivo) {
        return new TipoActivoDtos.TipoActivoResponse(
                tipoActivo.getIdTipoActivo(),
                tipoActivo.getNombre(),
                tipoActivo.getCategoria(),
                tipoActivo.getDescripcion(),
                tipoActivo.isRequiereMantenimiento(),
                tipoActivo.getVidaUtilAnios()
        );
    }
}
