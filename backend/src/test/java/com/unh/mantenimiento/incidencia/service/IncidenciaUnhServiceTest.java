package com.unh.mantenimiento.incidencia.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.incidencia.dto.IncidenciaDtos;
import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import com.unh.mantenimiento.incidencia.repository.IncidenciaUnhRepository;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del modulo de incidencias. Corre contra PostgreSQL real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidenciaUnhServiceTest {

    @Autowired
    private IncidenciaUnhService incidenciaService;

    @Autowired
    private ActivoUnhService activoService;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

    @Autowired
    private IncidenciaUnhRepository incidenciaRepository;

    private Integer idTipo;
    private Integer idUbicacion;

    @BeforeEach
    void prepararCatalogos() {
        TipoActivoUnh tipo = new TipoActivoUnh();
        tipo.setNombre("Equipo de prueba " + System.nanoTime());
        tipo.setCategoria(TipoActivoUnh.CATEGORIA_EQUIPO);
        tipo.setRequiereMantenimiento(true);
        idTipo = tipoActivoRepository.save(tipo).getIdTipoActivo();

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre("Sala de prueba " + System.nanoTime());
        ubicacion.setTipoUbicacion("SALA");
        idUbicacion = ubicacionRepository.save(ubicacion).getIdUbicacion();
    }

    /** Crea un activo operativo y devuelve su ID. */
    private Integer crearActivo(String codigo, String nombre) {
        ActivoDtos.ActivoResponse activo = activoService.crear(
                new ActivoDtos.CrearActivoRequest(
                        codigo, nombre, idTipo, idUbicacion,
                        null, null, null, null, null,
                        ActivoUnh.OPERATIVO, null, null));
        return activo.id();
    }

    private IncidenciaDtos.CrearIncidenciaRequest request(Integer idActivo, String descripcion) {
        return new IncidenciaDtos.CrearIncidenciaRequest(
                idActivo, descripcion, IncidenciaUnh.MEDIA, null, null);
    }

    @Test
    @DisplayName("Reportar una incidencia la crea en estado ABIERTA con el activo anidado")
    void reportarIncidencia() {
        Integer idActivo = crearActivo("PAT-101", "Terma del Comedor");

        IncidenciaDtos.IncidenciaResponse creada =
                incidenciaService.crear(request(idActivo, "La terma no calienta"));

        assertThat(creada.id()).isNotNull();
        assertThat(creada.estado()).isEqualTo(IncidenciaUnh.ABIERTA);
        assertThat(creada.prioridad()).isEqualTo(IncidenciaUnh.MEDIA);
        assertThat(creada.descripcion()).isEqualTo("La terma no calienta");
        assertThat(creada.fechaDeteccion()).isNotNull();
        assertThat(creada.fechaCierre()).isNull();

        // El activo viene ANIDADO.
        assertThat(creada.activo()).isNotNull();
        assertThat(creada.activo().id()).isEqualTo(idActivo);
        assertThat(creada.activo().nombre()).isEqualTo("Terma del Comedor");
    }

    @Test
    @DisplayName("Reportar sobre un activo inexistente se rechaza")
    void activoInexistente() {
        assertThatThrownBy(() -> incidenciaService.crear(request(999999, "Falla")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Un activo desactivado no admite incidencias nuevas")
    void activoDesactivadoNoRecibeIncidencias() {
        Integer idActivo = crearActivo("PAT-102", "Lavadora dada de baja");
        activoService.desactivar(idActivo);

        assertThatThrownBy(() -> incidenciaService.crear(request(idActivo, "No prende")))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("desactivado");
    }

    @Test
    @DisplayName("Se puede corregir una incidencia ABIERTA")
    void corregirIncidenciaAbierta() {
        Integer idActivo = crearActivo("PAT-103", "Lavadora 1");
        IncidenciaDtos.IncidenciaResponse creada =
                incidenciaService.crear(request(idActivo, "Fuga de agua"));

        IncidenciaDtos.IncidenciaResponse actualizada = incidenciaService.actualizar(
                creada.id(),
                new IncidenciaDtos.ActualizarIncidenciaRequest(
                        "Fuga de agua importante", IncidenciaUnh.ALTA, "Revisar manguera"));

        assertThat(actualizada.descripcion()).isEqualTo("Fuga de agua importante");
        assertThat(actualizada.prioridad()).isEqualTo(IncidenciaUnh.ALTA);
        assertThat(actualizada.observaciones()).isEqualTo("Revisar manguera");
        assertThat(actualizada.estado()).isEqualTo(IncidenciaUnh.ABIERTA);
    }

    @Test
    @DisplayName("Una incidencia que ya no esta ABIERTA no se puede corregir")
    void noCorregirIncidenciaNoAbierta() {
        Integer idActivo = crearActivo("PAT-104", "Computadora 1");
        IncidenciaDtos.IncidenciaResponse creada =
                incidenciaService.crear(request(idActivo, "No enciende"));

        // El modulo de mantenimientos (I5) es quien cambia el estado.
        // Aqui se simula su transicion para verificar la regla de congelacion.
        forzarEstado(creada.id(), IncidenciaUnh.EN_ATENCION);

        assertThatThrownBy(() -> incidenciaService.actualizar(
                creada.id(),
                new IncidenciaDtos.ActualizarIncidenciaRequest(
                        "Otro texto", IncidenciaUnh.BAJA, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("ABIERTA");
    }

    @Test
    @DisplayName("Listar filtra por estado")
    void listarPorEstado() {
        Integer idActivo = crearActivo("PAT-105", "Terma 1");
        IncidenciaDtos.IncidenciaResponse creada =
                incidenciaService.crear(request(idActivo, "Falla 1"));
        forzarEstado(creada.id(), IncidenciaUnh.CERRADA);

        List<IncidenciaDtos.IncidenciaResponse> abiertas = incidenciaService.listar("ABIERTA", null);
        List<IncidenciaDtos.IncidenciaResponse> cerradas = incidenciaService.listar("CERRADA", null);
        List<IncidenciaDtos.IncidenciaResponse> todas = incidenciaService.listar(null, null);

        assertThat(abiertas).noneMatch(i -> i.id().equals(creada.id()));
        assertThat(cerradas).anyMatch(i -> i.id().equals(creada.id()));
        assertThat(todas).anyMatch(i -> i.id().equals(creada.id()));
    }

    @Test
    @DisplayName("Listar filtra por activo")
    void listarPorActivo() {
        Integer idActivoA = crearActivo("PAT-106", "Terma A");
        Integer idActivoB = crearActivo("PAT-107", "Terma B");

        incidenciaService.crear(request(idActivoA, "Falla en A"));
        incidenciaService.crear(request(idActivoB, "Falla en B"));

        List<IncidenciaDtos.IncidenciaResponse> deA = incidenciaService.listar(null, idActivoA);

        assertThat(deA).hasSize(1);
        assertThat(deA.get(0).activo().id()).isEqualTo(idActivoA);
    }

    @Test
    @DisplayName("Buscar una incidencia inexistente da 404")
    void buscarInexistente() {
        assertThatThrownBy(() -> incidenciaService.buscar(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    /**
     * Cambia el estado de una incidencia como lo hara el modulo de
     * mantenimientos (I5) cuando se crea o se cierra un correctivo.
     *
     * Se hace por JPA y no por SQL directo: la entidad queda gestionada en el
     * contexto de persistencia, asi que el Service ve el estado nuevo. Un
     * UPDATE por JDBC directo cambiaria la fila pero no la entidad en memoria,
     * y el Service seguiria leyendo el estado viejo.
     */
    private void forzarEstado(Integer idIncidencia, String estado) {
        IncidenciaUnh incidencia = incidenciaRepository.findById(idIncidencia).orElseThrow();
        incidencia.setEstado(estado);
        incidenciaRepository.saveAndFlush(incidencia);
    }
}
