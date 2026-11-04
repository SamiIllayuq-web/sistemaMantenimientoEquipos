package com.unh.mantenimiento.activo.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del modulo de activos. Corre contra PostgreSQL real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivoUnhServiceTest {

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

    private ActivoDtos.CrearActivoRequest request(String codigo, String nombre) {
        return new ActivoDtos.CrearActivoRequest(
                codigo,
                nombre,
                idTipo,
                idUbicacion,
                "Termowatt",
                "TW-500",
                "SERIE-123",
                LocalDate.of(2023, 5, 10),
                LocalDate.of(2023, 6, 1),
                ActivoUnh.OPERATIVO,
                new BigDecimal("1500.50"),
                "Terma del comedor"
        );
    }

    @Test
    @DisplayName("Crear activo lo registra con tipo y ubicacion anidados")
    void crearActivo() {
        ActivoDtos.ActivoResponse creado =
                activoService.crear(request("PAT-001", "Terma del Comedor"));

        assertThat(creado.id()).isNotNull();
        assertThat(creado.codigoPatrimonial()).isEqualTo("PAT-001");
        assertThat(creado.nombre()).isEqualTo("Terma del Comedor");
        assertThat(creado.estadoOperativo()).isEqualTo(ActivoUnh.OPERATIVO);
        assertThat(creado.estado()).isEqualTo(ActivoUnh.ESTADO_ACTIVO);
        assertThat(creado.valorAdquisicion()).isEqualByComparingTo("1500.50");

        // Tipo y ubicacion vienen ANIDADOS (activos y ubicaciones estan
        // separados en tablas, pero el frontend los necesita juntos).
        assertThat(creado.tipoActivo()).isNotNull();
        assertThat(creado.tipoActivo().id()).isEqualTo(idTipo);
        assertThat(creado.ubicacion()).isNotNull();
        assertThat(creado.ubicacion().id()).isEqualTo(idUbicacion);
    }

    @Test
    @DisplayName("El codigo patrimonial es unico")
    void codigoPatrimonialUnico() {
        activoService.crear(request("PAT-002", "Lavadora 1"));

        assertThatThrownBy(() -> activoService.crear(request("PAT-002", "Lavadora 2")))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    @DisplayName("Un activo con ubicacion o tipo inexistente se rechaza")
    void referenciasInvalidas() {
        ActivoDtos.CrearActivoRequest sinUbicacion = new ActivoDtos.CrearActivoRequest(
                "PAT-003", "Sin ubicacion", idTipo, 999999,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> activoService.crear(sinUbicacion))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar muestra solo los activos ACTIVOS por defecto")
    void listarSoloActivosPorDefecto() {
        ActivoDtos.ActivoResponse activo = activoService.crear(request("PAT-004", "Activo Activo"));
        activoService.desactivar(activo.id());

        List<ActivoDtos.ActivoResponse> lista = activoService.listar(null, null);

        assertThat(lista).noneMatch(a -> a.id().equals(activo.id()));
    }

    @Test
    @DisplayName("Listar con estado INACTIVO muestra los desactivados")
    void listarInactivos() {
        ActivoDtos.ActivoResponse activo = activoService.crear(request("PAT-005", "Para desactivar"));
        activoService.desactivar(activo.id());

        List<ActivoDtos.ActivoResponse> lista = activoService.listar(null, "INACTIVO");

        assertThat(lista).anyMatch(a -> a.id().equals(activo.id()));
    }

    @Test
    @DisplayName("La busqueda textual cubre nombre y codigo patrimonial")
    void busquedaTextual() {
        activoService.crear(request("PAT-006", "Terma Solar"));
        activoService.crear(request("PAT-007", "Lavadora Industrial"));

        List<ActivoDtos.ActivoResponse> porNombre = activoService.listar("solar", null);
        assertThat(porNombre).hasSize(1);
        assertThat(porNombre.get(0).nombre()).isEqualTo("Terma Solar");

        List<ActivoDtos.ActivoResponse> porCodigo = activoService.listar("PAT-007", null);
        assertThat(porCodigo).hasSize(1);
        assertThat(porCodigo.get(0).codigoPatrimonial()).isEqualTo("PAT-007");
    }

    @Test
    @DisplayName("Actualizar cambia los datos del activo")
    void actualizarActivo() {
        ActivoDtos.ActivoResponse creado = activoService.crear(request("PAT-008", "Nombre viejo"));

        ActivoDtos.ActivoResponse actualizado = activoService.actualizar(
                creado.id(),
                new ActivoDtos.ActualizarActivoRequest(
                        "Nombre nuevo", null, null, "Marca nueva", null, null,
                        null, null, ActivoUnh.FUERA_DE_SERVICIO, null, null));

        assertThat(actualizado.nombre()).isEqualTo("Nombre nuevo");
        assertThat(actualizado.marca()).isEqualTo("Marca nueva");
        assertThat(actualizado.estadoOperativo()).isEqualTo(ActivoUnh.FUERA_DE_SERVICIO);
    }

    @Test
    @DisplayName("Desactivar no borra: el activo sigue existiendo")
    void desactivarNoBorra() {
        ActivoDtos.ActivoResponse creado = activoService.crear(request("PAT-009", "A desactivar"));

        ActivoDtos.ActivoResponse desactivado = activoService.desactivar(creado.id());

        assertThat(desactivado.estado()).isEqualTo(ActivoUnh.ESTADO_INACTIVO);
        // Sigue existiendo y se puede consultar.
        assertThat(activoService.buscar(creado.id()).id()).isEqualTo(creado.id());
    }

    @Test
    @DisplayName("Desactivar dos veces se rechaza")
    void desactivarDosVeces() {
        ActivoDtos.ActivoResponse creado = activoService.crear(request("PAT-010", "Doble"));
        activoService.desactivar(creado.id());

        assertThatThrownBy(() -> activoService.desactivar(creado.id()))
                .isInstanceOf(OperacionInvalidaException.class);
    }

    @Test
    @DisplayName("Reactivar devuelve el activo a ACTIVO")
    void reactivar() {
        ActivoDtos.ActivoResponse creado = activoService.crear(request("PAT-011", "A reactivar"));
        activoService.desactivar(creado.id());

        ActivoDtos.ActivoResponse reactivado = activoService.reactivar(creado.id());

        assertThat(reactivado.estado()).isEqualTo(ActivoUnh.ESTADO_ACTIVO);
    }

    @Test
    @DisplayName("Buscar un activo inexistente da 404")
    void buscarInexistente() {
        assertThatThrownBy(() -> activoService.buscar(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
