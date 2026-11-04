package com.unh.mantenimiento.ubicacion.service;

import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.ubicacion.dto.UbicacionDtos;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de ubicaciones (catalogo). Deep module: interfaz pequena, logica
 * densa adentro (unicidad del nombre, guard de borrado).
 */
@Service
public class UbicacionService {

    private final UbicacionUnhRepository ubicacionRepository;
    private final EntityManager entityManager;

    public UbicacionService(UbicacionUnhRepository ubicacionRepository, EntityManager entityManager) {
        this.ubicacionRepository = ubicacionRepository;
        this.entityManager = entityManager;
    }

    /** Lectura: abierta a cualquier usuario con sesion (para llenar desplegables). */
    @Transactional(readOnly = true)
    public List<UbicacionDtos.UbicacionResponse> listar() {
        return ubicacionRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UbicacionDtos.UbicacionResponse buscar(Integer id) {
        return aResponse(obtener(id));
    }

    /** Escritura: exige el modulo UBICACIONES (lo valida el servidor). */
    @Transactional
    public UbicacionDtos.UbicacionResponse crear(UbicacionDtos.UbicacionRequest request) {
        if (ubicacionRepository.existsByNombre(request.nombre())) {
            throw new RecursoDuplicadoException("Ya existe una ubicacion con ese nombre");
        }

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre(request.nombre().trim());
        ubicacion.setTipoUbicacion(request.tipoUbicacion().trim());
        ubicacion.setPabellon(request.pabellon());
        ubicacion.setPiso(request.piso());
        ubicacion.setDescripcion(request.descripcion());
        ubicacionRepository.save(ubicacion);

        return aResponse(ubicacion);
    }

    @Transactional
    public UbicacionDtos.UbicacionResponse actualizar(Integer id, UbicacionDtos.UbicacionRequest request) {
        UbicacionUnh ubicacion = obtener(id);

        if (!request.nombre().trim().equals(ubicacion.getNombre())
                && ubicacionRepository.existsByNombre(request.nombre().trim())) {
            throw new RecursoDuplicadoException("Ya existe una ubicacion con ese nombre");
        }

        ubicacion.setNombre(request.nombre().trim());
        ubicacion.setTipoUbicacion(request.tipoUbicacion().trim());
        ubicacion.setPabellon(request.pabellon());
        ubicacion.setPiso(request.piso());
        ubicacion.setDescripcion(request.descripcion());
        ubicacionRepository.save(ubicacion);

        return aResponse(ubicacion);
    }

    /**
     * Elimina una ubicacion. Si hay activos que la referencian, NO se borra:
     * se rechaza con un mensaje claro (los registros con datos relacionados no
     * se destruyen).
     */
    @Transactional
    public void eliminar(Integer id) {
        UbicacionUnh ubicacion = obtener(id);

        long activosVinculados = contarActivosConUbicacion(id);
        if (activosVinculados > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar: hay " + activosVinculados
                            + " activo(s) que usan esta ubicacion");
        }

        ubicacionRepository.delete(ubicacion);
    }

    private long contarActivosConUbicacion(Integer idUbicacion) {
        Object resultado = entityManager
                .createNativeQuery("SELECT COUNT(*) FROM activo_unh WHERE id_ubicacion = :id")
                .setParameter("id", idUbicacion)
                .getSingleResult();
        return ((Number) resultado).longValue();
    }

    private UbicacionUnh obtener(Integer id) {
        return ubicacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicacion con ID " + id));
    }

    private UbicacionDtos.UbicacionResponse aResponse(UbicacionUnh ubicacion) {
        return new UbicacionDtos.UbicacionResponse(
                ubicacion.getIdUbicacion(),
                ubicacion.getNombre(),
                ubicacion.getTipoUbicacion(),
                ubicacion.getPabellon(),
                ubicacion.getPiso(),
                ubicacion.getDescripcion()
        );
    }
}
