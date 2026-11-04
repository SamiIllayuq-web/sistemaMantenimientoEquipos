package com.unh.mantenimiento.ubicacion.service;

import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.ubicacion.dto.UbicacionDtos;
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
 * Tests del catalogo de ubicaciones. Corre contra PostgreSQL real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UbicacionServiceTest {

    @Autowired
    private UbicacionService ubicacionService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final UbicacionDtos.UbicacionRequest HABITACION_101 =
            new UbicacionDtos.UbicacionRequest(
                    "Habitacion 101", "HABITACION", "Pabellon A", "1", "Cuarto del primer piso");

    @Test
    @DisplayName("Crear ubicacion la registra y la devuelve")
    void crearUbicacion() {
        UbicacionDtos.UbicacionResponse creada = ubicacionService.crear(HABITACION_101);

        assertThat(creada.id()).isNotNull();
        assertThat(creada.nombre()).isEqualTo("Habitacion 101");
        assertThat(creada.tipoUbicacion()).isEqualTo("HABITACION");
        assertThat(creada.pabellon()).isEqualTo("Pabellon A");
        assertThat(creada.piso()).isEqualTo("1");
    }

    @Test
    @DisplayName("Listar devuelve las ubicaciones ordenadas por nombre")
    void listarOrdenado() {
        ubicacionService.crear(HABITACION_101);
        ubicacionService.crear(new UbicacionDtos.UbicacionRequest(
                "Comedor", "COMEDOR", null, null, null));

        List<UbicacionDtos.UbicacionResponse> lista = ubicacionService.listar();

        assertThat(lista).isNotEmpty();
        assertThat(lista).extracting(UbicacionDtos.UbicacionResponse::nombre)
                .isSorted();
    }

    @Test
    @DisplayName("No se permiten dos ubicaciones con el mismo nombre")
    void nombreDuplicado() {
        ubicacionService.crear(HABITACION_101);

        assertThatThrownBy(() -> ubicacionService.crear(HABITACION_101))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    @DisplayName("Actualizar cambia los datos de la ubicacion")
    void actualizarUbicacion() {
        UbicacionDtos.UbicacionResponse creada = ubicacionService.crear(HABITACION_101);

        UbicacionDtos.UbicacionResponse actualizada = ubicacionService.actualizar(
                creada.id(),
                new UbicacionDtos.UbicacionRequest(
                        "Habitacion 102", "HABITACION", "Pabellon B", "1", "Renombrada"));

        assertThat(actualizada.id()).isEqualTo(creada.id());
        assertThat(actualizada.nombre()).isEqualTo("Habitacion 102");
        assertThat(actualizada.pabellon()).isEqualTo("Pabellon B");
    }

    @Test
    @DisplayName("Actualizar a un nombre ya usado se rechaza")
    void actualizarANombreDuplicado() {
        ubicacionService.crear(HABITACION_101);
        UbicacionDtos.UbicacionResponse otra = ubicacionService.crear(
                new UbicacionDtos.UbicacionRequest("Comedor", "COMEDOR", null, null, null));

        assertThatThrownBy(() -> ubicacionService.actualizar(
                otra.id(), HABITACION_101))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    @DisplayName("Buscar una ubicacion inexistente da 404")
    void buscarInexistente() {
        assertThatThrownBy(() -> ubicacionService.buscar(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Eliminar una ubicacion sin activos funciona")
    void eliminarSinActivos() {
        UbicacionDtos.UbicacionResponse creada = ubicacionService.crear(HABITACION_101);

        ubicacionService.eliminar(creada.id());

        assertThatThrownBy(() -> ubicacionService.buscar(creada.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una ubicacion con activos NO se puede eliminar")
    void noEliminarConActivos() {
        UbicacionDtos.UbicacionResponse creada = ubicacionService.crear(HABITACION_101);

        // Se inserta un activo que referencia la ubicacion (SQL nativo para
        // no acoplar este test al modulo de activos, que aun no existe).
        entityManager.createNativeQuery("""
                INSERT INTO tipo_activo_unh (nombre, categoria, requiere_mantenimiento)
                VALUES ('Equipo de prueba', 'EQUIPO', true)
                """).executeUpdate();

        Integer idTipoActivo = (Integer) entityManager
                .createNativeQuery("SELECT id_tipo_activo FROM tipo_activo_unh WHERE nombre = 'Equipo de prueba'")
                .getSingleResult();

        entityManager.createNativeQuery("""
                INSERT INTO activo_unh (id_tipo_activo, id_ubicacion, codigo_patrimonial, nombre,
                                    estado_operativo, fecha_registro)
                VALUES (:idTipo, :idUbicacion, 'PAT-TEST-001', 'Activo de prueba', 'OPERATIVO', NOW())
                """)
                .setParameter("idTipo", idTipoActivo)
                .setParameter("idUbicacion", creada.id())
                .executeUpdate();

        assertThatThrownBy(() -> ubicacionService.eliminar(creada.id()))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("activo");
    }
}
