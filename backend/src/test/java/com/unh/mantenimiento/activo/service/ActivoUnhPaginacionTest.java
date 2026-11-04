package com.unh.mantenimiento.activo.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
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
 * Tests de la paginacion del modulo de activos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivoUnhPaginacionTest {

    @Autowired
    private ActivoUnhService activoService;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

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

    private void crearActivos(int cantidad, String prefijo) {
        for (int i = 1; i <= cantidad; i++) {
            activoService.crear(new ActivoDtos.CrearActivoRequest(
                    prefijo + "-" + i, "Activo " + prefijo + " " + i, idTipo, idUbicacion,
                    null, null, null, null, null,
                    ActivoUnh.OPERATIVO, null, null));
        }
    }

    @Test
    @DisplayName("La primera pagina trae el tamano pedido y el total de todos")
    void primeraPagina() {
        crearActivos(25, "PAG");

        PaginaResponse<ActivoDtos.ActivoResponse> pagina =
                activoService.listarPaginado(null, null, 1, 10);

        assertThat(pagina.contenido()).hasSize(10);
        assertThat(pagina.total()).isEqualTo(25);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(10);
    }

    @Test
    @DisplayName("La ultima pagina trae solo los que faltan")
    void ultimaPagina() {
        crearActivos(25, "PAG");

        PaginaResponse<ActivoDtos.ActivoResponse> pagina =
                activoService.listarPaginado(null, null, 3, 10);

        assertThat(pagina.contenido()).hasSize(5);
        assertThat(pagina.total()).isEqualTo(25);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una pagina mas alla del total devuelve vacio, sin error")
    void paginaFueraDeRango() {
        crearActivos(5, "PAG");

        PaginaResponse<ActivoDtos.ActivoResponse> pagina =
                activoService.listarPaginado(null, null, 10, 10);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.total()).isEqualTo(5);
    }

    @Test
    @DisplayName("El filtro se aplica antes de paginar: el total cuenta lo filtrado")
    void filtroSeAplicaAntesDePaginar() {
        crearActivos(15, "PAG");
        ActivoDtos.ActivoResponse objetivo = activoService.crear(
                new ActivoDtos.CrearActivoRequest("ESPECIAL", "Activo Unico XYZ",
                        idTipo, idUbicacion, null, null, null, null, null,
                        ActivoUnh.OPERATIVO, null, null));

        PaginaResponse<ActivoDtos.ActivoResponse> pagina =
                activoService.listarPaginado("unico", null, 1, 10);

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).id()).isEqualTo(objetivo.id());
    }

    @Test
    @DisplayName("El filtro por estado tambien cuenta solo lo filtrado")
    void filtroPorEstado() {
        crearActivos(3, "PAG");
        ActivoDtos.ActivoResponse inactivo = activoService.crear(
                new ActivoDtos.CrearActivoRequest("INACT-1", "Para desactivar",
                        idTipo, idUbicacion, null, null, null, null, null,
                        ActivoUnh.OPERATIVO, null, null));
        activoService.desactivar(inactivo.id());

        PaginaResponse<ActivoDtos.ActivoResponse> inactivos =
                activoService.listarPaginado(null, "INACTIVO", 1, 10);
        PaginaResponse<ActivoDtos.ActivoResponse> activos =
                activoService.listarPaginado(null, "ACTIVO", 1, 10);

        assertThat(inactivos.total()).isEqualTo(1);
        assertThat(inactivos.contenido()).hasSize(1);
        assertThat(activos.total()).isEqualTo(3);
        assertThat(activos.contenido()).hasSize(3);
    }

    @Test
    @DisplayName("Las paginas no se repiten entre si")
    void paginasNoSeRepiten() {
        crearActivos(20, "PAG");

        PaginaResponse<ActivoDtos.ActivoResponse> primera =
                activoService.listarPaginado(null, null, 1, 10);
        PaginaResponse<ActivoDtos.ActivoResponse> segunda =
                activoService.listarPaginado(null, null, 2, 10);

        assertThat(primera.contenido()).doesNotContainAnyElementsOf(segunda.contenido());
        assertThat(primera.contenido()).hasSize(10);
        assertThat(segunda.contenido()).hasSize(10);
    }
}
