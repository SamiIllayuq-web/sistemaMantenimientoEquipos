package com.unh.mantenimiento.activo.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.repository.ActivoUnhRepository;
import com.unh.mantenimiento.comun.Paginacion;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de activos. Deep module: interfaz pequena, logica densa adentro
 * (unicidad del codigo patrimonial, tipo y ubicacion obligatorios, y la
 * regla de no borrar sino desactivar).
 */
@Service
public class ActivoUnhService {

    private final ActivoUnhRepository activoRepository;
    private final TipoActivoUnhRepository tipoActivoRepository;
    private final UbicacionUnhRepository ubicacionRepository;

    public ActivoUnhService(ActivoUnhRepository activoRepository,
                            TipoActivoUnhRepository tipoActivoRepository,
                            UbicacionUnhRepository ubicacionRepository) {
        this.activoRepository = activoRepository;
        this.tipoActivoRepository = tipoActivoRepository;
        this.ubicacionRepository = ubicacionRepository;
    }

    /**
     * Listado con busqueda opcional por nombre o codigo patrimonial, y filtro
     * opcional por estado de gestion. Por defecto solo muestra los ACTIVOS.
     */
    @Transactional(readOnly = true)
    public List<ActivoDtos.ActivoResponse> listar(String texto, String estado) {
        // El patron se construye aqui (no en la query) para evitar el CONCAT
        // con parametro nulo, que rompe el LOWER() en Hibernate 7.
        String patron = construirPatron(texto);
        String estadoLimpio = (estado == null || estado.isBlank()) ? ActivoUnh.ESTADO_ACTIVO : estado;

        return activoRepository.buscar(patron, estadoLimpio)
                .stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Listado paginado con los mismos filtros. La pagina es BASE 1: la
     * primera pagina es la 1.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ActivoDtos.ActivoResponse> listarPaginado(
            String texto, String estado, int pagina, int tamano) {
        String patron = construirPatron(texto);
        String estadoLimpio = (estado == null || estado.isBlank()) ? ActivoUnh.ESTADO_ACTIVO : estado;

        int paginaSegura = Paginacion.normalizarPagina(pagina);
        int tamanoSeguro = Paginacion.normalizarTamano(tamano);
        Pageable pageable = Paginacion.aPageable(
                paginaSegura, tamanoSeguro, Sort.by(Sort.Direction.ASC, "nombre"));

        Page<ActivoUnh> resultado = activoRepository.buscarPaginado(patron, estadoLimpio, pageable);

        return PaginaResponse.de(
                resultado.getContent().stream().map(this::aResponse).toList(),
                paginaSegura,
                tamanoSeguro,
                resultado.getTotalElements());
    }

    /** Construye el patron de busqueda. Ver nota de LOWER() en el repository. */
    private String construirPatron(String texto) {
        return (texto == null || texto.isBlank())
                ? null
                : "%" + texto.trim().toLowerCase() + "%";
    }

    /**
     * Lista ligera de activos para los desplegables. Trae solo id, codigo y
     * nombre (sin tipo ni ubicacion). Se mantiene aparte del listado paginado
     * para que el selector no se rompa cuando hay mas activos que paginas.
     */
    @Transactional(readOnly = true)
    public List<ActivoDtos.ActivoOpcion> listarOpciones() {
        return activoRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(a -> new ActivoDtos.ActivoOpcion(
                        a.getIdActivo(), a.getCodigoPatrimonial(), a.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivoDtos.ActivoResponse buscar(Integer id) {
        return aResponse(obtener(id));
    }

    /** Alta de activo. */
    @Transactional
    public ActivoDtos.ActivoResponse crear(ActivoDtos.CrearActivoRequest request) {
        if (activoRepository.existsByCodigoPatrimonial(request.codigoPatrimonial().trim())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un activo con el codigo patrimonial " + request.codigoPatrimonial());
        }

        ActivoUnh activo = new ActivoUnh();
        activo.setCodigoPatrimonial(request.codigoPatrimonial().trim());
        activo.setNombre(request.nombre().trim());
        activo.setTipoActivo(obtenerTipoActivo(request.idTipoActivo()));
        activo.setUbicacion(obtenerUbicacion(request.idUbicacion()));
        activo.setMarca(request.marca());
        activo.setModelo(request.modelo());
        activo.setNumeroSerie(request.numeroSerie());
        activo.setFechaAdquisicion(request.fechaAdquisicion());
        activo.setFechaPuestaServicio(request.fechaPuestaServicio());
        activo.setEstadoOperativo(
                request.estadoOperativo() == null || request.estadoOperativo().isBlank()
                        ? ActivoUnh.OPERATIVO
                        : request.estadoOperativo());
        activo.setValorAdquisicion(request.valorAdquisicion());
        activo.setObservaciones(request.observaciones());
        activo.setEstado(ActivoUnh.ESTADO_ACTIVO);
        activo.setFechaRegistro(LocalDateTime.now());

        activoRepository.save(activo);

        return aResponse(activo);
    }

    @Transactional
    public ActivoDtos.ActivoResponse actualizar(
            Integer id, ActivoDtos.ActualizarActivoRequest request) {
        ActivoUnh activo = obtener(id);

        if (request.nombre() != null) {
            activo.setNombre(request.nombre().trim());
        }
        if (request.idTipoActivo() != null) {
            activo.setTipoActivo(obtenerTipoActivo(request.idTipoActivo()));
        }
        if (request.idUbicacion() != null) {
            activo.setUbicacion(obtenerUbicacion(request.idUbicacion()));
        }
        if (request.marca() != null) {
            activo.setMarca(request.marca());
        }
        if (request.modelo() != null) {
            activo.setModelo(request.modelo());
        }
        if (request.numeroSerie() != null) {
            activo.setNumeroSerie(request.numeroSerie());
        }
        if (request.fechaAdquisicion() != null) {
            activo.setFechaAdquisicion(request.fechaAdquisicion());
        }
        if (request.fechaPuestaServicio() != null) {
            activo.setFechaPuestaServicio(request.fechaPuestaServicio());
        }
        if (request.estadoOperativo() != null && !request.estadoOperativo().isBlank()) {
            activo.setEstadoOperativo(request.estadoOperativo());
        }
        if (request.valorAdquisicion() != null) {
            activo.setValorAdquisicion(request.valorAdquisicion());
        }
        if (request.observaciones() != null) {
            activo.setObservaciones(request.observaciones());
        }

        activoRepository.save(activo);

        return aResponse(activo);
    }

    /**
     * Desactiva el activo. NO se borra: el inventario se conserva.
     * Un activo ya inactivo no se puede volver a desactivar.
     */
    @Transactional
    public ActivoDtos.ActivoResponse desactivar(Integer id) {
        ActivoUnh activo = obtener(id);

        if (!activo.esActivo()) {
            throw new OperacionInvalidaException("El activo ya esta inactivo");
        }

        activo.setEstado(ActivoUnh.ESTADO_INACTIVO);
        activoRepository.save(activo);

        return aResponse(activo);
    }

    /** Reactiva un activo desactivado. */
    @Transactional
    public ActivoDtos.ActivoResponse reactivar(Integer id) {
        ActivoUnh activo = obtener(id);

        if (activo.esActivo()) {
            throw new OperacionInvalidaException("El activo ya esta activo");
        }

        activo.setEstado(ActivoUnh.ESTADO_ACTIVO);
        activoRepository.save(activo);

        return aResponse(activo);
    }

    private ActivoUnh obtener(Integer id) {
        return activoRepository.buscarDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un activo con ID " + id));
    }

    private TipoActivoUnh obtenerTipoActivo(Integer id) {
        return tipoActivoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un tipo de activo con ID " + id));
    }

    private UbicacionUnh obtenerUbicacion(Integer id) {
        return ubicacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicacion con ID " + id));
    }

    private ActivoDtos.ActivoResponse aResponse(ActivoUnh activo) {
        TipoActivoUnh tipo = activo.getTipoActivo();
        UbicacionUnh ubicacion = activo.getUbicacion();

        return new ActivoDtos.ActivoResponse(
                activo.getIdActivo(),
                activo.getCodigoPatrimonial(),
                activo.getNombre(),
                activo.getMarca(),
                activo.getModelo(),
                activo.getNumeroSerie(),
                activo.getFechaAdquisicion(),
                activo.getFechaPuestaServicio(),
                activo.getEstadoOperativo(),
                activo.getEstado(),
                activo.getValorAdquisicion(),
                activo.getObservaciones(),
                new ActivoDtos.TipoActivoResumen(
                        tipo.getIdTipoActivo(),
                        tipo.getNombre(),
                        tipo.getCategoria()
                ),
                new ActivoDtos.UbicacionResumen(
                        ubicacion.getIdUbicacion(),
                        ubicacion.getNombre(),
                        ubicacion.getTipoUbicacion()
                )
        );
    }
}
