package com.unh.mantenimiento.comun.excepcion;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.repository.ActivoUnhRepository;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.incidencia.dto.IncidenciaDtos;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.incidencia.repository.IncidenciaUnhRepository;
import com.unh.mantenimiento.incidencia.service.IncidenciaUnhService;
import com.unh.mantenimiento.mantenimiento.dto.MantenimientoDtos;
import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import com.unh.mantenimiento.mantenimiento.repository.MantenimientoUnhRepository;
import com.unh.mantenimiento.mantenimiento.service.MantenimientoUnhService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I7 Cierre: prueba de INTEGRACION del flujo completo de negocio.
 *
 * Recorre el camino real de una falla, de principio a fin:
 * activo -> incidencia -> correctivo -> trabajo -> cierre.
 *
 * Esta prueba es la que verifica que las REGLAS DE NEGOCIO se respetan entre
 * modulos (los tests de cada modulo no ven a los demas).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlujoCompletoIntegrationTest {

    @Autowired
    private ActivoUnhService activoService;

    @Autowired
    private IncidenciaUnhService incidenciaService;

    @Autowired
    private MantenimientoUnhService mantenimientoService;

    @Autowired
    private ActivoUnhRepository activoRepository;

    @Autowired
    private IncidenciaUnhRepository incidenciaRepository;

    @Autowired
    private MantenimientoUnhRepository mantenimientoRepository;

    @Autowired
    private ResponsableUnhRepository responsableRepository;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String DNI = "88888888";

    private Integer idTipo;
    private Integer idUbicacion;

    @BeforeEach
    void preparar() {
        ResponsableUnh responsable = new ResponsableUnh();
        responsable.setDni(DNI);
        responsable.setNombres("Responsable");
        responsable.setApellidos("De Integracion");
        responsable.setCorreo("integracion." + System.nanoTime() + "@unh.edu.pe");
        responsable.setClaveHash("x");
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();

        UsuarioAutenticado usuario = new UsuarioAutenticado(
                DNI, responsable.getCorreo(), "Responsable De Integracion", "ACTIVO",
                List.of("MOD_ACTIVOS", "MOD_INCIDENCIAS", "MOD_MANTENIMIENTOS", "MOD_EVIDENCIAS"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));

        TipoActivoUnh tipo = new TipoActivoUnh();
        tipo.setNombre("Tipo integracion " + System.nanoTime());
        tipo.setCategoria(TipoActivoUnh.CATEGORIA_EQUIPO);
        tipo.setRequiereMantenimiento(true);
        idTipo = tipoActivoRepository.save(tipo).getIdTipoActivo();

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre("Sala integracion " + System.nanoTime());
        ubicacion.setTipoUbicacion("SALA");
        idUbicacion = ubicacionRepository.save(ubicacion).getIdUbicacion();
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private Integer crearActivo(String codigo) {
        return activoService.crear(new ActivoDtos.CrearActivoRequest(
                codigo, "Terma de integracion", idTipo, idUbicacion,
                null, null, null, null, null,
                ActivoUnh.OPERATIVO, null, null)).id();
    }

    @Test
    @DisplayName("Flujo completo: activo -> incidencia -> correctivo -> cierre")
    void flujoCompleto() {
        Integer idActivo = crearActivo("PAT-FLUJO-1");

        // 1. El activo arranca OPERATIVO.
        ActivoUnh activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);
        assertThat(activo.getEstado()).isEqualTo("ACTIVO");

        // 2. Se reporta una incidencia: nace ABIERTA y no toca el activo.
        IncidenciaDtos.IncidenciaResponse incidencia = incidenciaService.crear(
                new IncidenciaDtos.CrearIncidenciaRequest(
                        idActivo, "La terma no calienta", IncidenciaUnh.ALTA, null, null));
        assertThat(incidencia.estado()).isEqualTo(IncidenciaUnh.ABIERTA);

        activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);

        // 3. Se abre el correctivo: la incidencia pasa a EN_ATENCION y el
        //    activo a EN_MANTENIMIENTO.
        MantenimientoDtos.MantenimientoResponse mantenimiento =
                mantenimientoService.crearCorrectivo(
                        new MantenimientoDtos.CrearCorrectivoRequest(
                                incidencia.id(), LocalDateTime.now().minusHours(2), null));

        assertThat(mantenimiento.estadoMantenimiento()).isEqualTo(MantenimientoUnh.EN_PROCESO);
        assertThat(mantenimiento.incidencia().id()).isEqualTo(incidencia.id());
        assertThat(mantenimiento.responsable().dni()).isEqualTo(DNI);

        IncidenciaUnh incEnBd = incidenciaRepository.findById(incidencia.id()).orElseThrow();
        assertThat(incEnBd.getEstado()).isEqualTo(IncidenciaUnh.EN_ATENCION);

        activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.EN_MANTENIMIENTO);

        // 4. Mientras esta en proceso, la incidencia queda CONGELADA.
        assertThatThrownBy(() -> incidenciaService.actualizar(
                incidencia.id(),
                new IncidenciaDtos.ActualizarIncidenciaRequest(
                        "Otra descripcion", null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("ABIERTA");

        // 5. Se registra el trabajo realizado.
        MantenimientoDtos.MantenimientoResponse actualizado = mantenimientoService.actualizar(
                mantenimiento.id(),
                new MantenimientoDtos.ActualizarTrabajoRequest(
                        "Resistencia quemada", "Se cambio la resistencia", null,
                        null, null, null));

        assertThat(actualizado.diagnostico()).isEqualTo("Resistencia quemada");
        assertThat(actualizado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.EN_PROCESO);

        // 6. Se finaliza el trabajo.
        MantenimientoDtos.MantenimientoResponse finalizado = mantenimientoService.finalizar(
                mantenimiento.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, "Reparado", null, null, null, null));

        assertThat(finalizado.estadoMantenimiento()).isEqualTo(MantenimientoUnh.FINALIZADO);
        assertThat(finalizado.fechaFin()).isNotNull();

        // 7. La incidencia queda CERRADA y el activo vuelve a OPERATIVO.
        incEnBd = incidenciaRepository.findById(incidencia.id()).orElseThrow();
        assertThat(incEnBd.getEstado()).isEqualTo(IncidenciaUnh.CERRADA);
        assertThat(incEnBd.getFechaCierre()).isNotNull();

        activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);

        // 8. El historial del activo trae el trabajo.
        List<MantenimientoDtos.MantenimientoResponse> historial =
                mantenimientoService.historialDeActivo(idActivo);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).id()).isEqualTo(mantenimiento.id());

        // 9. El mantenimiento queda registrado en la base con su responsable.
        MantenimientoUnh enBd = mantenimientoRepository.findById(mantenimiento.id()).orElseThrow();
        assertThat(enBd.getResponsable().getDni()).isEqualTo(DNI);
        assertThat(enBd.getIncidencia().getIdIncidencia()).isEqualTo(incidencia.id());
    }

    @Test
    @DisplayName("Un activo desactivado no admite incidencias, pero su historial sigue")
    void activoDesactivadoNoAdmiteIncidencias() {
        Integer idActivo = crearActivo("PAT-FLUJO-2");

        IncidenciaDtos.IncidenciaResponse incidencia = incidenciaService.crear(
                new IncidenciaDtos.CrearIncidenciaRequest(
                        idActivo, "Falla antes de la baja", IncidenciaUnh.MEDIA, null, null));

        MantenimientoDtos.MantenimientoResponse mantenimiento =
                mantenimientoService.crearCorrectivo(
                        new MantenimientoDtos.CrearCorrectivoRequest(
                                incidencia.id(), null, null));

        // Se da de baja el activo: sigue inventariado, pero no admite fallas.
        activoService.desactivar(idActivo);

        assertThatThrownBy(() -> incidenciaService.crear(
                new IncidenciaDtos.CrearIncidenciaRequest(
                        idActivo, "Falla con el activo dado de baja", IncidenciaUnh.BAJA, null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("desactivado");

        // El historial sigue disponible: el bien sigue inventariado.
        List<MantenimientoDtos.MantenimientoResponse> historial =
                mantenimientoService.historialDeActivo(idActivo);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).id()).isEqualTo(mantenimiento.id());

        // Y por defecto el listado de activos lo excluye.
        assertThat(activoService.listarPaginado(null, "ACTIVO", 1, 50).contenido())
                .noneMatch(activo -> activo.id().equals(idActivo));
        assertThat(activoService.listarPaginado(null, "INACTIVO", 1, 50).contenido())
                .anyMatch(activo -> activo.id().equals(idActivo));
    }

    @Test
    @DisplayName("Un preventivo no cambia el estado del activo ni de ninguna incidencia")
    void preventivoNoCambiaEstados() {
        Integer idActivo = crearActivo("PAT-FLUJO-3");

        MantenimientoDtos.MantenimientoResponse preventivo =
                mantenimientoService.crearPreventivo(
                        new MantenimientoDtos.CrearPreventivoRequest(
                                idActivo, null, "Limpieza semestral"));

        assertThat(preventivo.incidencia()).isNull();
        assertThat(preventivo.tipoMantenimiento()).isEqualTo(MantenimientoUnh.PREVENTIVO);

        ActivoUnh activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);

        // Al finalizar un preventivo tampoco hay incidencia que cerrar.
        mantenimientoService.finalizar(preventivo.id(),
                new MantenimientoDtos.FinalizarRequest(
                        LocalDateTime.now(), null, null, null, null, null, null, null));

        activo = activoRepository.findById(idActivo).orElseThrow();
        assertThat(activo.getEstadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);
    }
}
