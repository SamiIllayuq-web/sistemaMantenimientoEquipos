package com.unh.mantenimiento.tipoactivo.service;

import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.tipoactivo.dto.TipoActivoDtos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del catalogo de tipos de activo. Corre contra PostgreSQL real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TipoActivoServiceTest {

    @Autowired
    private TipoActivoService tipoActivoService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final TipoActivoDtos.TipoActivoRequest TERMA =
            new TipoActivoDtos.TipoActivoRequest(
                    "Equipo de cocina", "EQUIPO", "Termas, hornos, freidoras", true, 10);

    @Test
    @DisplayName("Crear tipo de activo lo registra y lo devuelve")
    void crearTipoActivo() {
        TipoActivoDtos.TipoActivoResponse creado = tipoActivoService.crear(TERMA);

        assertThat(creado.id()).isNotNull();
        assertThat(creado.nombre()).isEqualTo("Equipo de cocina");
        assertThat(creado.categoria()).isEqualTo("EQUIPO");
        assertThat(creado.requiereMantenimiento()).isTrue();
        assertThat(creado.vidaUtilAnios()).isEqualTo(10);
    }

    @Test
    @DisplayName("Listar devuelve los tipos ordenados por nombre")
    void listarOrdenado() {
        tipoActivoService.crear(TERMA);
        tipoActivoService.crear(new TipoActivoDtos.TipoActivoRequest(
                "Instalacion electrica", "INFRAESTRUCTURA", "Tableros y cableado", false, null));

        List<TipoActivoDtos.TipoActivoResponse> lista = tipoActivoService.listar();

        assertThat(lista).extracting(TipoActivoDtos.TipoActivoResponse::nombre)
                .isSorted();
    }

    @Test
    @DisplayName("Filtrar por categoria devuelve solo esa categoria")
    void listarPorCategoria() {
        tipoActivoService.crear(TERMA);
        tipoActivoService.crear(new TipoActivoDtos.TipoActivoRequest(
                "Instalacion electrica", "INFRAESTRUCTURA", null, false, null));

        List<TipoActivoDtos.TipoActivoResponse> soloEquipos =
                tipoActivoService.listarPorCategoria("EQUIPO");

        assertThat(soloEquipos).isNotEmpty();
        assertThat(soloEquipos)
                .extracting(TipoActivoDtos.TipoActivoResponse::categoria)
                .containsOnly("EQUIPO");
    }

    @Test
    @DisplayName("No se permiten dos tipos con el mismo nombre")
    void nombreDuplicado() {
        tipoActivoService.crear(TERMA);

        assertThatThrownBy(() -> tipoActivoService.crear(TERMA))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    @DisplayName("Actualizar cambia los datos del tipo de activo")
    void actualizarTipoActivo() {
        TipoActivoDtos.TipoActivoResponse creado = tipoActivoService.crear(TERMA);

        TipoActivoDtos.TipoActivoResponse actualizado = tipoActivoService.actualizar(
                creado.id(),
                new TipoActivoDtos.TipoActivoRequest(
                        "Equipo de cocina y lavanderia", "EQUIPO", null, false, 5));

        assertThat(actualizado.id()).isEqualTo(creado.id());
        assertThat(actualizado.nombre()).isEqualTo("Equipo de cocina y lavanderia");
        assertThat(actualizado.requiereMantenimiento()).isFalse();
        assertThat(actualizado.vidaUtilAnios()).isEqualTo(5);
    }

    @Test
    @DisplayName("Buscar un tipo inexistente da 404")
    void buscarInexistente() {
        assertThatThrownBy(() -> tipoActivoService.buscar(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Eliminar un tipo sin activos funciona")
    void eliminarSinActivos() {
        TipoActivoDtos.TipoActivoResponse creado = tipoActivoService.crear(TERMA);

        tipoActivoService.eliminar(creado.id());

        assertThatThrownBy(() -> tipoActivoService.buscar(creado.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Un tipo con activos NO se puede eliminar")
    void noEliminarConActivos() {
        TipoActivoDtos.TipoActivoResponse creado = tipoActivoService.crear(TERMA);

        // Activo que referencia el tipo (SQL nativo: el modulo de activos aun
        // no existe y no se quiere acoplar este test a el).
        entityManager.createNativeQuery("""
                INSERT INTO ubicacion_unh (nombre, tipo_ubicacion)
                VALUES ('UbicacionUnh de prueba', 'SALA')
                """).executeUpdate();

        Integer idUbicacion = (Integer) entityManager
                .createNativeQuery("SELECT id_ubicacion FROM ubicacion_unh WHERE nombre = 'UbicacionUnh de prueba'")
                .getSingleResult();

        entityManager.createNativeQuery("""
                INSERT INTO activo_unh (id_tipo_activo, id_ubicacion, codigo_patrimonial, nombre,
                                    estado_operativo, fecha_registro)
                VALUES (:idTipo, :idUbicacion, 'PAT-TEST-002', 'Activo de prueba', 'OPERATIVO', NOW())
                """)
                .setParameter("idTipo", creado.id())
                .setParameter("idUbicacion", idUbicacion)
                .executeUpdate();

        assertThatThrownBy(() -> tipoActivoService.eliminar(creado.id()))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("activo");
    }
}
