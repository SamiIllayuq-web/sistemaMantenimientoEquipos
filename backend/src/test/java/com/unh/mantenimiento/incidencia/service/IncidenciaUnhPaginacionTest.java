package com.unh.mantenimiento.incidencia.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la paginacion del modulo de incidencias.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidenciaUnhPaginacionTest {

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
    private Integer idActivo;

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

        ActivoDtos.ActivoResponse activo = activoService.crear(
                new ActivoDtos.CrearActivoRequest(
                        "PAG-1", "Activo de prueba", idTipo, idUbicacion,
                        null, null, null, null, null,
                        ActivoUnh.OPERATIVO, null, null));
        idActivo = activo.id();
    }

    private void crearIncidencias(int cantidad) {
        for (int i = 1; i <= cantidad; i++) {
            incidenciaService.crear(new IncidenciaDtos.CrearIncidenciaRequest(
                    idActivo, "Falla numero " + i, IncidenciaUnh.MEDIA, null, null));
        }
    }

    @Test
    @DisplayName("La primera pagina trae el tamano pedido y el total de todas")
    void primeraPagina() {
        crearIncidencias(25);

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> pagina =
                incidenciaService.listarPaginado(null, null, 1, 10);

        assertThat(pagina.contenido()).hasSize(10);
        assertThat(pagina.total()).isEqualTo(25);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(10);
    }

    @Test
    @DisplayName("La ultima pagina trae solo los que faltan")
    void ultimaPagina() {
        crearIncidencias(25);

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> pagina =
                incidenciaService.listarPaginado(null, null, 3, 10);

        assertThat(pagina.contenido()).hasSize(5);
        assertThat(pagina.total()).isEqualTo(25);
    }

    @Test
    @DisplayName("Una pagina fuera de rango devuelve vacio, sin error")
    void paginaFueraDeRango() {
        crearIncidencias(5);

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> pagina =
                incidenciaService.listarPaginado(null, null, 10, 10);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.total()).isEqualTo(5);
    }

    @Test
    @DisplayName("El filtro por estado cuenta solo lo filtrado")
    void filtroPorEstado() {
        crearIncidencias(4);
        IncidenciaDtos.IncidenciaResponse una = incidenciaService.crear(
                new IncidenciaDtos.CrearIncidenciaRequest(
                        idActivo, "Otra", IncidenciaUnh.MEDIA, null, null));
        forzarEstado(una.id(), IncidenciaUnh.CERRADA);

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> abiertas =
                incidenciaService.listarPaginado("ABIERTA", null, 1, 10);
        PaginaResponse<IncidenciaDtos.IncidenciaResponse> cerradas =
                incidenciaService.listarPaginado("CERRADA", null, 1, 10);

        assertThat(abiertas.total()).isEqualTo(4);
        assertThat(abiertas.contenido()).hasSize(4);
        assertThat(cerradas.total()).isEqualTo(1);
        assertThat(cerradas.contenido()).hasSize(1);
    }

    @Test
    @DisplayName("El filtro por activo cuenta solo lo de ese activo")
    void filtroPorActivo() {
        crearIncidencias(3);

        ActivoDtos.ActivoResponse otro = activoService.crear(
                new ActivoDtos.CrearActivoRequest(
                        "PAG-2", "Otro activo", idTipo, idUbicacion,
                        null, null, null, null, null,
                        ActivoUnh.OPERATIVO, null, null));
        incidenciaService.crear(new IncidenciaDtos.CrearIncidenciaRequest(
                otro.id(), "Falla de otro", IncidenciaUnh.MEDIA, null, null));

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> delPrimero =
                incidenciaService.listarPaginado(null, idActivo, 1, 10);
        PaginaResponse<IncidenciaDtos.IncidenciaResponse> todos =
                incidenciaService.listarPaginado(null, null, 1, 10);

        assertThat(delPrimero.total()).isEqualTo(3);
        assertThat(todos.total()).isEqualTo(4);
    }

    @Test
    @DisplayName("Las paginas no se repiten entre si")
    void paginasNoSeRepiten() {
        crearIncidencias(20);

        PaginaResponse<IncidenciaDtos.IncidenciaResponse> primera =
                incidenciaService.listarPaginado(null, null, 1, 10);
        PaginaResponse<IncidenciaDtos.IncidenciaResponse> segunda =
                incidenciaService.listarPaginado(null, null, 2, 10);

        assertThat(primera.contenido()).doesNotContainAnyElementsOf(segunda.contenido());
        assertThat(primera.contenido()).hasSize(10);
        assertThat(segunda.contenido()).hasSize(10);
    }

    /** Cambia el estado por JPA (ver nota de persistence context en la skill). */
    private void forzarEstado(Integer idIncidencia, String estado) {
        IncidenciaUnh incidencia = incidenciaRepository.findById(idIncidencia).orElseThrow();
        incidencia.setEstado(estado);
        incidenciaRepository.saveAndFlush(incidencia);
    }
}
