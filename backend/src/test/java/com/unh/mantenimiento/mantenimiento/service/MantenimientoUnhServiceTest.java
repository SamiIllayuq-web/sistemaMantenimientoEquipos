package com.unh.mantenimiento.mantenimiento.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.incidencia.dto.IncidenciaDtos;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.incidencia.repository.IncidenciaUnhRepository;
import com.unh.mantenimiento.incidencia.service.IncidenciaUnhService;
import com.unh.mantenimiento.mantenimiento.dto.MantenimientoDtos;
import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import com.unh.mantenimiento.mantenimiento.repository.MantenimientoUnhRepository;
import com.unh.mantenimiento.seguridad.model.UsuarioAutenticado;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import com.unh.mantenimiento.usuario.model.ResponsableUnh;
import com.unh.mantenimiento.usuario.repository.ResponsableUnhRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del modulo de mantenimientos. Corre contra PostgreSQL real.
 *
 * El responsable del mantenimiento es SIEMPRE el usuario con sesion, asi que
 * los tests simulan el contexto de seguridad de Spring.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MantenimientoUnhServiceTest {

    @Autowired
    private MantenimientoUnhService mantenimientoService;

    @Autowired
    private IncidenciaUnhService incidenciaService;

    @Autowired
    private ActivoUnhService activoService;

    @Autowired
    private MantenimientoUnhRepository mantenimientoRepository;

    @Autowired
    private IncidenciaUnhRepository incidenciaRepository;

    @Autowired
    private com.unh.mantenimiento.activo.repository.ActivoUnhRepository activoRepository;

    @Autowired
    private ResponsableUnhRepository responsableRepository;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String DNI = "55555555";

    private Integer idTipo;
    private Integer idUbicacion;
    private Integer idActivo;

    @BeforeEach
    void preparar() {
        // El usuario con sesion: sera el responsable de los mantenimientos.
        ResponsableUnh responsable = new ResponsableUnh();
        responsable.setDni(DNI);
        responsable.setNombres("Tecnico");
        responsable.setApellidos("De Prueba");
        responsable.setCorreo("tecnico." + System.nanoTime() + "@unh.edu.pe");
        responsable.setClaveHash("x");
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();

        UsuarioAutenticado usuario = new UsuarioAutenticado(
                DNI, responsable.getCorreo(), "Tecnico De Prueba", "ACTIVO",
                List.of("MOD_MANTENIMIENTOS", "MOD_ACTIVOS", "MOD_INCIDENCIAS"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));

        TipoActivoUnh tipo = new TipoActivoUnh();
        tipo.setNombre("Equipo de prueba " + System.nanoTime());
        tipo.setCategoria(TipoActivoUnh.CATEGORIA_EQUIPO);
        tipo.setRequiereMantenimiento(true);
        idTipo = tipoActivoRepository.save(tipo).getIdTipoActivo();

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre("Sala de prueba " + System.nanoTime());
        ubicacion.setTipoUbicacion("SALA");
        idUbicacion = ubicacionRepository.save(ubicacion).getIdUbicacion();

        idActivo = crearActivo("PAT-MNT-1", "Terma del Comedor");
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private Integer crearActivo(String codigo, String nombre) {
        return activoService.crear(new ActivoDtos.CrearActivoRequest(
                codigo, nombre, idTipo, idUbicacion,
                null, null, null, null, null,
                ActivoUnh.OPERATIVO, null, null)).id();
    }

    private IncidenciaDtos.IncidenciaResponse crearIncidencia(Integer activo) {
        return incidenciaService.crear(new IncidenciaDtos.CrearIncidenciaRequest(
                activo, "La terma no calienta", IncidenciaUnh.ALTA, null, null));
    }

    @Test
    @DisplayName("Crear un correctivo pone la incidencia EN_ATENCION y el activo EN_MANTENIMIENTO")
    void crearCorrectivoCambiaEstados() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);

        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(
                        incidencia.id(), null, "Se revisa la resistencia"));

        assertThat(creado.id()).isNotNull();
        assertThat(creado.tipoMantenimiento()).isEqualTo(MantenimientoUnh.CORRECTIVO);
        assertThat(creado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.EN_PROCESO);
        assertThat(creado.fechaInicio()).isNotNull();
        assertThat(creado.fechaFin()).isNull();

        // La incidencia paso a EN_ATENCION.
        IncidenciaUnh enBd = incidenciaRepository.findById(incidencia.id()).orElseThrow();
        assertThat(enBd.getEstado()).isEqualTo(IncidenciaUnh.EN_ATENCION);

        // El activo paso a EN_MANTENIMIENTO.
        ActivoUnh activoEnBd = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activoEnBd.getEstadoOperativo()).isEqualTo(ActivoUnh.EN_MANTENIMIENTO);
    }

    @Test
    @DisplayName("El responsable del mantenimiento es el usuario con sesion")
    void responsableEsElUsuarioConSesion() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);

        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        assertThat(creado.responsable()).isNotNull();
        assertThat(creado.responsable().dni()).isEqualTo(DNI);
        assertThat(creado.responsable().nombreCompleto()).isEqualTo("Tecnico De Prueba");
    }

    @Test
    @DisplayName("Finalizar cierra la incidencia y devuelve el activo a OPERATIVO")
    void finalizarCierraIncidenciaYActivo() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        MantenimientoDtos.MantenimientoResponse finalizado = mantenimientoService.finalizar(
                creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now().plusHours(2),
                        "Resistencia quemada",
                        "Se cambio la resistencia",
                        "La terma calienta con normalidad",
                        new BigDecimal("3.5"),
                        new BigDecimal("120.00"),
                        null,
                        null));

        assertThat(finalizado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.FINALIZADO);
        assertThat(finalizado.fechaFin()).isNotNull();
        assertThat(finalizado.diagnostico()).isEqualTo("Resistencia quemada");
        assertThat(finalizado.actividadRealizada()).isEqualTo("Se cambio la resistencia");
        assertThat(finalizado.resultado()).isEqualTo("La terma calienta con normalidad");
        assertThat(finalizado.horasInactividad()).isEqualByComparingTo("3.5");
        assertThat(finalizado.costoManoObra()).isEqualByComparingTo("120.00");

        // La incidencia quedo CERRADA con fecha de cierre.
        IncidenciaUnh incEnBd = incidenciaRepository.findById(incidencia.id()).orElseThrow();
        assertThat(incEnBd.getEstado()).isEqualTo(IncidenciaUnh.CERRADA);
        assertThat(incEnBd.getFechaCierre()).isNotNull();
    }

    @Test
    @DisplayName("Una incidencia que ya tiene mantenimiento no admite un segundo")
    void incidenciaNoRepetida() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        assertThatThrownBy(() -> mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("ya tiene un mantenimiento");
    }

    @Test
    @DisplayName("Una incidencia que ya no esta ABIERTA no admite un correctivo")
    void incidenciaNoAbierta() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        forzarEstadoIncidencia(incidencia.id(), IncidenciaUnh.CERRADA);

        assertThatThrownBy(() -> mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null)))
                .isInstanceOf(OperacionInvalidaException.class);
    }

    @Test
    @DisplayName("Un preventivo se registra sin incidencia")
    void crearPreventivo() {
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearPreventivo(
                new MantenimientoDtos.CrearPreventivoRequest(idActivo, null, "Limpieza anual"));

        assertThat(creado.tipoMantenimiento()).isEqualTo(MantenimientoUnh.PREVENTIVO);
        assertThat(creado.incidencia()).isNull();
        assertThat(creado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.EN_PROCESO);
        assertThat(creado.activo().id()).isEqualTo(idActivo);
    }

    @Test
    @DisplayName("Finalizar dos veces se rechaza")
    void finalizarDosVeces() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        mantenimientoService.finalizar(creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null));

        assertThatThrownBy(() -> mantenimientoService.finalizar(creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("ya esta en estado");
    }

    @Test
    @DisplayName("Una fecha de fin anterior al inicio se rechaza")
    void fechaFinAnteriorAlInicio() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(
                        incidencia.id(), LocalDateTime.now().plusDays(2), null));

        assertThatThrownBy(() -> mantenimientoService.finalizar(creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("no puede ser anterior");
    }

    @Test
    @DisplayName("Un mantenimiento terminado no se puede modificar")
    void noModificarTerminado() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        mantenimientoService.finalizar(creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null));

        assertThatThrownBy(() -> mantenimientoService.actualizar(creado.id(),
                new MantenimientoDtos.ActualizarTrabajoRequest(
                        "otro diagnostico", null, null, null, null, null)))
                .isInstanceOf(OperacionInvalidaException.class);
    }

    @Test
    @DisplayName("El historial de un activo trae sus mantenimientos")
    void historialPorActivo() {
        Integer otroActivo = crearActivo("PAT-MNT-2", "Lavadora");

        incidenciaService.crear(new IncidenciaDtos.CrearIncidenciaRequest(
                idActivo, "Falla 1", IncidenciaUnh.MEDIA, null, null));
        IncidenciaDtos.IncidenciaResponse segunda = incidenciaService.crear(
                new IncidenciaDtos.CrearIncidenciaRequest(
                        idActivo, "Falla 2", IncidenciaUnh.MEDIA, null, null));
        incidenciaService.crear(new IncidenciaDtos.CrearIncidenciaRequest(
                otroActivo, "Falla de otro", IncidenciaUnh.MEDIA, null, null));

        mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(segunda.id(), null, null));

        List<MantenimientoDtos.MantenimientoResponse> historial =
                mantenimientoService.historialDeActivo(idActivo);

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).activo().id()).isEqualTo(idActivo);
    }

    @Test
    @DisplayName("El historial de un activo inexistente da 404")
    void historialDeActivoInexistente() {
        assertThatThrownBy(() -> mantenimientoService.historialDeActivo(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar filtra por estado, activo y tipo")
    void listarConFiltros() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse correctivo = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));
        MantenimientoDtos.MantenimientoResponse preventivo = mantenimientoService.crearPreventivo(
                new MantenimientoDtos.CrearPreventivoRequest(idActivo, null, null));

        PaginaResponse<MantenimientoDtos.MantenimientoResponse> enProceso =
                mantenimientoService.listarPaginado("EN_PROCESO", null, null, 1, 20);
        PaginaResponse<MantenimientoDtos.MantenimientoResponse> correctivos =
                mantenimientoService.listarPaginado(null, null, "CORRECTIVO", 1, 20);
        PaginaResponse<MantenimientoDtos.MantenimientoResponse> deOtroActivo =
                mantenimientoService.listarPaginado(null, 999999, null, 1, 20);

        assertThat(enProceso.total()).isEqualTo(2);
        assertThat(correctivos.total()).isEqualTo(1);
        assertThat(correctivos.contenido().get(0).id()).isEqualTo(correctivo.id());
        assertThat(deOtroActivo.total()).isZero();
        assertThat(preventivo.tipoMantenimiento()).isEqualTo(MantenimientoUnh.PREVENTIVO);
    }

    @Test
    @DisplayName("Actualizar el trabajo en curso guarda diagnostico y actividad")
    void actualizarTrabajoEnProceso() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        MantenimientoDtos.MantenimientoResponse actualizado = mantenimientoService.actualizar(
                creado.id(),
                new MantenimientoDtos.ActualizarTrabajoRequest(
                        "Falla en el cable", "Se reviso el cableado", null,
                        new BigDecimal("1.5"), new BigDecimal("50.00"), "Pendiente de repuesto"));

        assertThat(actualizado.diagnostico()).isEqualTo("Falla en el cable");
        assertThat(actualizado.actividadRealizada()).isEqualTo("Se reviso el cableado");
        assertThat(actualizado.horasInactividad()).isEqualByComparingTo("1.5");
        assertThat(actualizado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.EN_PROCESO);
    }

    @Test
    @DisplayName("Finalizar en el mismo minuto del inicio se acepta")
    void finalizarEnElMismoMinuto() {
        // El frontend pide la hora con precision de MINUTOS (HH:MM) y el
        // inicio se guardo con segundos. Si el trabajo se cierra en el mismo
        // minuto en que empezo, la fecha de fin (07:03:00) seria anterior al
        // inicio (07:03:45): el backend debe tomarlo como "ahora" y no
        // rechazarlo.
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(incidencia.id(), null, null));

        LocalDateTime inicio = creado.fechaInicio();
        LocalDateTime mismoMinuto = inicio.truncatedTo(ChronoUnit.MINUTES);

        MantenimientoDtos.MantenimientoResponse finalizado = mantenimientoService.finalizar(
                creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        mismoMinuto, null, null, null, null, null, null, null));

        assertThat(finalizado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.FINALIZADO);
        assertThat(finalizado.fechaFin()).isNotNull();

        // La incidencia se cierra con la misma fecha.
        IncidenciaUnh incEnBd = incidenciaRepository.findById(incidencia.id()).orElseThrow();
        assertThat(incEnBd.getEstado()).isEqualTo(IncidenciaUnh.CERRADA);
        assertThat(incEnBd.getFechaCierre()).isNotNull();
    }

    @Test
    @DisplayName("Una fecha de fin realmente anterior al inicio se rechaza")
    void fechaFinAnteriorAlInicioDeVerdad() {
        IncidenciaDtos.IncidenciaResponse incidencia = crearIncidencia(idActivo);
        MantenimientoDtos.MantenimientoResponse creado = mantenimientoService.crearCorrectivo(
                new MantenimientoDtos.CrearCorrectivoRequest(
                        incidencia.id(), LocalDateTime.now().plusDays(2), null));

        assertThatThrownBy(() -> mantenimientoService.finalizar(creado.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("no puede ser anterior");
    }

    @Test
    @DisplayName("Buscar un mantenimiento inexistente da 404")
    void buscarInexistente() {
        assertThatThrownBy(() -> mantenimientoService.buscar(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    /** Cambia el estado de una incidencia por JPA (ver nota de la skill). */
    private void forzarEstadoIncidencia(Integer id, String estado) {
        IncidenciaUnh incidencia = incidenciaRepository.findById(id).orElseThrow();
        incidencia.setEstado(estado);
        incidenciaRepository.saveAndFlush(incidencia);
    }
}
