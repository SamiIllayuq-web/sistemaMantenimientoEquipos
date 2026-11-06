package com.unh.mantenimiento.mantenimiento.service;

import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.repository.ActivoUnhRepository;
import com.unh.mantenimiento.comun.Paginacion;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.incidencia.repository.IncidenciaUnhRepository;
import com.unh.mantenimiento.mantenimiento.dto.MantenimientoDtos;
import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import com.unh.mantenimiento.mantenimiento.repository.MantenimientoUnhRepository;
import com.unh.mantenimiento.usuario.model.ResponsableUnh;
import com.unh.mantenimiento.usuario.repository.ResponsableUnhRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio de mantenimientos. Deep module: interfaz pequena, logica densa
 * adentro (transiciones de estado de los tres registros que toca).
 *
 * Las transiciones son el corazon del modulo:
 * - Al CREAR un correctivo: incidencia -> EN_ATENCION, activo -> EN_MANTENIMIENTO.
 * - Al FINALIZAR: incidencia -> CERRADA (con fecha), activo -> OPERATIVO.
 * - El PREVENTIVO no tiene incidencia y no cambia el estado de ninguna.
 */
@Service
public class MantenimientoUnhService {

    private final MantenimientoUnhRepository mantenimientoRepository;
    private final IncidenciaUnhRepository incidenciaRepository;
    private final ActivoUnhRepository activoRepository;
    private final ResponsableUnhRepository responsableRepository;

    public MantenimientoUnhService(MantenimientoUnhRepository mantenimientoRepository,
                                   IncidenciaUnhRepository incidenciaRepository,
                                   ActivoUnhRepository activoRepository,
                                   ResponsableUnhRepository responsableRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.activoRepository = activoRepository;
        this.responsableRepository = responsableRepository;
    }

    /** Listado paginado con filtros por estado, activo y tipo. */
    @Transactional(readOnly = true)
    public PaginaResponse<MantenimientoDtos.MantenimientoResponse> listarPaginado(
            String estado, Integer idActivo, String tipo, int pagina, int tamano) {
        String estadoLimpio = (estado == null || estado.isBlank()) ? null : estado.trim();
        Integer activoLimpio = (idActivo == null) ? null : idActivo;
        String tipoLimpio = (tipo == null || tipo.isBlank()) ? null : tipo.trim();

        int paginaSegura = Paginacion.normalizarPagina(pagina);
        int tamanoSeguro = Paginacion.normalizarTamano(tamano);
        Pageable pageable = Paginacion.aPageable(
                paginaSegura,
                tamanoSeguro,
                Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("idMantenimiento")));

        Page<MantenimientoUnh> resultado = mantenimientoRepository
                .buscarPaginado(estadoLimpio, activoLimpio, tipoLimpio, pageable);

        return PaginaResponse.de(
                resultado.getContent().stream().map(this::aResponse).toList(),
                paginaSegura,
                tamanoSeguro,
                resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MantenimientoDtos.MantenimientoResponse buscar(Integer id) {
        return aResponse(obtener(id));
    }

    /**
     * Historial de un activo: la lista de sus mantenimientos.
     * Es la consulta que usa el boton "Historial" de la pantalla de Activos.
     */
    @Transactional(readOnly = true)
    public List<MantenimientoDtos.MantenimientoResponse> historialDeActivo(Integer idActivo) {
        // Verifica que el activo exista: un historial de un activo inexistente
        // es un 404, no una lista vacia silenciosa.
        if (!activoRepository.existsById(idActivo)) {
            throw new RecursoNoEncontradoException("No existe un activo con ID " + idActivo);
        }

        return mantenimientoRepository.historialPorActivo(idActivo)
                .stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Crea un mantenimiento CORRECTIVO a partir de una incidencia.
     * La incidencia pasa a EN_ATENCION y el activo a EN_MANTENIMIENTO.
     */
    @Transactional
    public MantenimientoDtos.MantenimientoResponse crearCorrectivo(
            MantenimientoDtos.CrearCorrectivoRequest request) {
        IncidenciaUnh incidencia = obtenerIncidencia(request.idIncidencia());

        // Primero el guard mas especifico: si la incidencia ya tiene un
        // mantenimiento, el mensaje lo dice. Sin este orden el guard
        // siguiente (estado != ABIERTA) lo taparia siempre.
        if (mantenimientoRepository.contarPorIncidencia(incidencia.getIdIncidencia()) > 0) {
            throw new OperacionInvalidaException(
                    "La incidencia ya tiene un mantenimiento asociado");
        }

        if (!incidencia.esAbierta()) {
            throw new OperacionInvalidaException(
                    "La incidencia ya esta en estado " + incidencia.getEstado()
                            + ": no se puede abrir un mantenimiento sobre ella");
        }

        ActivoUnh activo = incidencia.getActivo();

        MantenimientoUnh mantenimiento = new MantenimientoUnh();
        mantenimiento.setIncidencia(incidencia);
        mantenimiento.setActivo(activo);
        mantenimiento.setTipoMantenimiento(MantenimientoUnh.CORRECTIVO);
        mantenimiento.setEstadoMantenimiento(MantenimientoUnh.EN_PROCESO);
        mantenimiento.setFechaInicio(
                request.fechaInicio() == null ? LocalDateTime.now() : request.fechaInicio());
        mantenimiento.setObservaciones(request.observaciones());
        mantenimiento.setCostoManoObra(BigDecimal.ZERO);
        mantenimiento.setResponsable(obtenerResponsableActual());

        // Transiciones de estado.
        incidencia.setEstado(IncidenciaUnh.EN_ATENCION);
        incidenciaRepository.save(incidencia);
        activo.setEstadoOperativo(ActivoUnh.EN_MANTENIMIENTO);
        activoRepository.save(activo);

        mantenimientoRepository.save(mantenimiento);

        return aResponse(mantenimiento);
    }

    /**
     * Crea un mantenimiento PREVENTIVO: se registra a mano, sin incidencia.
     * No cambia el estado de ninguna incidencia (no tiene) ni del activo.
     */
    @Transactional
    public MantenimientoDtos.MantenimientoResponse crearPreventivo(
            MantenimientoDtos.CrearPreventivoRequest request) {
        ActivoUnh activo = obtenerActivo(request.idActivo());

        MantenimientoUnh mantenimiento = new MantenimientoUnh();
        mantenimiento.setActivo(activo);
        mantenimiento.setIncidencia(null);
        mantenimiento.setTipoMantenimiento(MantenimientoUnh.PREVENTIVO);
        mantenimiento.setEstadoMantenimiento(MantenimientoUnh.EN_PROCESO);
        mantenimiento.setFechaInicio(
                request.fechaInicio() == null ? LocalDateTime.now() : request.fechaInicio());
        mantenimiento.setObservaciones(request.observaciones());
        mantenimiento.setCostoManoObra(BigDecimal.ZERO);
        mantenimiento.setResponsable(obtenerResponsableActual());

        mantenimientoRepository.save(mantenimiento);

        return aResponse(mantenimiento);
    }

    /**
     * Actualiza el trabajo en curso: diagnostico, actividad realizada y los
     * datos de seguimiento. Solo mientras esta EN_PROCESO.
     */
    @Transactional
    public MantenimientoDtos.MantenimientoResponse actualizar(
            Integer id, MantenimientoDtos.ActualizarTrabajoRequest request) {
        MantenimientoUnh mantenimiento = obtener(id);

        if (!mantenimiento.esEnProceso()) {
            throw new OperacionInvalidaException(
                    "Solo se puede modificar un mantenimiento EN_PROCESO. "
                            + "Este esta en estado " + mantenimiento.getEstadoMantenimiento());
        }

        if (request.diagnostico() != null) {
            mantenimiento.setDiagnostico(request.diagnostico());
        }
        if (request.actividadRealizada() != null) {
            mantenimiento.setActividadRealizada(request.actividadRealizada());
        }
        if (request.resultado() != null) {
            mantenimiento.setResultado(request.resultado());
        }
        if (request.horasInactividad() != null) {
            mantenimiento.setHorasInactividad(request.horasInactividad());
        }
        if (request.costoManoObra() != null) {
            mantenimiento.setCostoManoObra(request.costoManoObra());
        }
        if (request.observaciones() != null) {
            mantenimiento.setObservaciones(request.observaciones());
        }

        mantenimientoRepository.save(mantenimiento);

        return aResponse(mantenimiento);
    }

    /**
     * Finaliza el trabajo. Cierra la incidencia (si es correctivo) y devuelve
     * el activo a OPERATIVO.
     */
    @Transactional
    public MantenimientoDtos.MantenimientoResponse finalizar(
            Integer id, MantenimientoDtos.FinalizarRequest request) {
        MantenimientoUnh mantenimiento = obtener(id);

        if (!mantenimiento.esEnProceso()) {
            throw new OperacionInvalidaException(
                    "Este mantenimiento ya esta en estado " + mantenimiento.getEstadoMantenimiento());
        }

        // La fecha de fin no puede ser anterior a la de inicio.
        //
        // El frontend pide la hora con precision de MINUTOS (HH:MM), pero el
        // inicio se guardo con segundos. Si el trabajo se finaliza en el
        // mismo minuto en que empezo, la fecha de fin (07:03:00) seria
        // anterior al inicio (07:03:45) y se rechazaria sin motivo.
        //
        // Cuando ambas caen en el mismo minuto, el usuario esta diciendo
        // "ahora": se usa el momento real de la finalizacion.
        LocalDateTime fechaFin = request.fechaFin();
        if (fechaFin.isBefore(mantenimiento.getFechaInicio())) {
            boolean mismoMinuto = fechaFin.truncatedTo(ChronoUnit.MINUTES)
                    .equals(mantenimiento.getFechaInicio().truncatedTo(ChronoUnit.MINUTES));

            if (!mismoMinuto) {
                throw new OperacionInvalidaException(
                        "La fecha de fin no puede ser anterior a la fecha de inicio");
            }

            fechaFin = LocalDateTime.now();
        }

        mantenimiento.setFechaFin(fechaFin);
        mantenimiento.setEstadoMantenimiento(MantenimientoUnh.FINALIZADO);

        if (request.diagnostico() != null) {
            mantenimiento.setDiagnostico(request.diagnostico());
        }
        if (request.actividadRealizada() != null) {
            mantenimiento.setActividadRealizada(request.actividadRealizada());
        }
        if (request.resultado() != null) {
            mantenimiento.setResultado(request.resultado());
        }
        if (request.horasInactividad() != null) {
            mantenimiento.setHorasInactividad(request.horasInactividad());
        }
        if (request.costoManoObra() != null) {
            mantenimiento.setCostoManoObra(request.costoManoObra());
        }
        if (request.proximaFechaMantenimiento() != null
                && !request.proximaFechaMantenimiento().isBlank()) {
            mantenimiento.setProximaFechaMantenimiento(
                    LocalDate.parse(request.proximaFechaMantenimiento()));
        }
        if (request.observaciones() != null) {
            mantenimiento.setObservaciones(request.observaciones());
        }

        // Transiciones de estado.
        if (mantenimiento.esCorrectivo() && mantenimiento.getIncidencia() != null) {
            IncidenciaUnh incidencia = incidenciaRepository
                    .findById(mantenimiento.getIncidencia().getIdIncidencia())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe la incidencia del mantenimiento"));
            incidencia.setEstado(IncidenciaUnh.CERRADA);
            incidencia.setFechaCierre(fechaFin);
            incidenciaRepository.save(incidencia);
        }

        ActivoUnh activo = activoRepository
                .findById(mantenimiento.getActivo().getIdActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el activo del mantenimiento"));
        activo.setEstadoOperativo(ActivoUnh.OPERATIVO);
        activoRepository.save(activo);

        mantenimientoRepository.save(mantenimiento);

        return aResponse(mantenimiento);
    }

    private MantenimientoUnh obtener(Integer id) {
        return mantenimientoRepository.buscarDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un mantenimiento con ID " + id));
    }

    private IncidenciaUnh obtenerIncidencia(Integer id) {
        return incidenciaRepository.buscarDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una incidencia con ID " + id));
    }

    private ActivoUnh obtenerActivo(Integer id) {
        return activoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un activo con ID " + id));
    }

    /** El responsable es SIEMPRE el usuario con sesion iniciada. */
    private ResponsableUnh obtenerResponsableActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof
                com.unh.mantenimiento.seguridad.model.UsuarioAutenticado usuario)) {
            throw new OperacionInvalidaException(
                    "No hay un usuario autenticado para registrar el mantenimiento");
        }

        return responsableRepository.findById(usuario.getDni())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el responsable con DNI " + usuario.getDni()));
    }

    private MantenimientoDtos.MantenimientoResponse aResponse(MantenimientoUnh mantenimiento) {
        ActivoUnh activo = mantenimiento.getActivo();
        ResponsableUnh responsable = mantenimiento.getResponsable();
        IncidenciaUnh incidencia = mantenimiento.getIncidencia();

        return new MantenimientoDtos.MantenimientoResponse(
                mantenimiento.getIdMantenimiento(),
                mantenimiento.getTipoMantenimiento(),
                mantenimiento.getEstadoMantenimiento(),
                mantenimiento.getFechaInicio(),
                mantenimiento.getFechaFin(),
                mantenimiento.getDiagnostico(),
                mantenimiento.getActividadRealizada(),
                mantenimiento.getResultado(),
                mantenimiento.getHorasInactividad(),
                mantenimiento.getCostoManoObra(),
                mantenimiento.getProximaFechaMantenimiento(),
                mantenimiento.getObservaciones(),
                new MantenimientoDtos.ActivoResumen(
                        activo.getIdActivo(),
                        activo.getCodigoPatrimonial(),
                        activo.getNombre()
                ),
                new MantenimientoDtos.ResponsableResumen(
                        responsable.getDni(),
                        responsable.nombreCompleto()
                ),
                incidencia == null
                        ? null
                        : new MantenimientoDtos.IncidenciaResumen(
                                incidencia.getIdIncidencia(),
                                incidencia.getDescripcion(),
                                incidencia.getEstado()
                        )
        );
    }
}
