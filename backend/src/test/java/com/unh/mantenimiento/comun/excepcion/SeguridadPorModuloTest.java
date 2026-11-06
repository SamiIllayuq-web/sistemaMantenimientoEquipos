package com.unh.mantenimiento.comun.excepcion;

import com.unh.mantenimiento.modulo.ModuloUnhRepository;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import com.unh.mantenimiento.usuario.repository.ResponsableUnhRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I7 Cierre:Revision de SEGURIDAD por modulo.
 *
 * Verifica con codigos de estado REALES que cada endpoint de escritura
 * rechaza a un usuario que no tiene su modulo. Las anotaciones
 * `@PreAuthorize` no demuestran nada: lo que importa es que el servidor
 * devuelva 403.
 *
 * Cada caso usa un usuario con UN SOLO modulo, para comprobar que el permiso
 * se evalua modulo por modulo y no "de bloque".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeguridadPorModuloTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResponsableUnhRepository responsableRepository;

    @Autowired
    private ModuloUnhRepository moduloRepository;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

    @Autowired
    private EntityManager entityManager;

    private int contador = 0;

    private Integer idTipo;
    private Integer idUbicacion;

    /**
     * Catalogo minimo: los endpoints de escritura exigen que el tipo y la
     * ubicacion referenciados existan (si no, la FK responde 404 y la prueba
     * mediria lo que no quiere).
     */
    @BeforeEach
    void prepararCatalogos() {
        TipoActivoUnh tipo = new TipoActivoUnh();
        tipo.setNombre("Tipo seguridad " + System.nanoTime());
        tipo.setCategoria(TipoActivoUnh.CATEGORIA_EQUIPO);
        tipo.setRequiereMantenimiento(true);
        idTipo = tipoActivoRepository.save(tipo).getIdTipoActivo();

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre("Sala seguridad " + System.nanoTime());
        ubicacion.setTipoUbicacion("SALA");
        idUbicacion = ubicacionRepository.save(ubicacion).getIdUbicacion();
        entityManager.flush();
    }

    /**
     * Extrae el "id" de una respuesta JSON de la API.
     */
    private int idDe(String json) {
        int inicio = json.indexOf("\"id\":") + "\"id\":".length();
        int fin = json.indexOf(',', inicio);
        if (fin < 0) {
            fin = json.indexOf('}', inicio);
        }
        return Integer.parseInt(json.substring(inicio, fin).trim());
    }

    /**
     * Crea un usuario con EXACTAMENTE un modulo, inicia sesion y devuelve su
     * token.
     */
    private String tokenConSolo(String dni, String modulo) {
        contador++;
        String correo = "modulo" + contador + "@unh.edu.pe";

        var responsable = new com.unh.mantenimiento.usuario.model.ResponsableUnh();
        responsable.setDni(dni);
        responsable.setNombres("Usuario");
        responsable.setApellidos("Modulo " + modulo);
        responsable.setCorreo(correo);
        responsable.setClaveHash(passwordEncoder.encode("ClaveSegura123"));
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(java.time.LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();

        var moduloUnh = moduloRepository.findByCodigo(modulo).orElseThrow();
        var asignacion = new com.unh.mantenimiento.modulo.ResponsableModuloUnh();
        asignacion.setIdResponsable(dni);
        asignacion.setIdModulo(moduloUnh.getIdModulo());
        entityManager.persist(asignacion);
        entityManager.flush();
        entityManager.clear();

        try {
            String cuerpo = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\":\"" + correo + "\",\"clave\":\"ClaveSegura123\"}"))
                    .andReturn().getResponse().getContentAsString();
            int inicio = cuerpo.indexOf("\"token\":\"") + "\"token\":\"".length();
            int fin = cuerpo.indexOf('"', inicio);
            return cuerpo.substring(inicio, fin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Sin token, TODOS los endpoints privados devuelven 401")
    void sinTokenEs401EnTodosLosModulos() throws Exception {
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ubicaciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tipos-activo")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/activos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incidencias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mantenimientos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/evidencias/mantenimiento/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un usuario con solo ACTIVOS no puede escribir en los demas modulos")
    void soloActivosNoEscribeEnLosDemas() throws Exception {
        String token = tokenConSolo("11111111", "ACTIVOS");
        String catalogos = "\"idTipoActivo\":" + idTipo + ",\"idUbicacion\":" + idUbicacion;

        // Su propio modulo: si puede.
        mockMvc.perform(post("/api/activos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoPatrimonial\":\"SEG-ACT-1\",\"nombre\":\"Activo\","
                                + catalogos + "}"))
                .andExpect(status().isCreated());

        // Los demas: no.
        mockMvc.perform(post("/api/ubicaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"No permitido\",\"tipoUbicacion\":\"SALA\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tipos-activo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"No permitido\",\"categoria\":\"EQUIPO\","
                                + "\"requiereMantenimiento\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":1,\"descripcion\":\"No permitido\",\"prioridad\":\"BAJA\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/mantenimientos/preventivo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"99999991\",\"nombres\":\"No\",\"apellidos\":\"Permitido\","
                                + "\"correo\":\"nopermitido@unh.edu.pe\",\"clave\":\"ClaveSegura123\","
                                + "\"modulos\":[\"ACTIVOS\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario con solo INCIDENCIAS no puede crear mantenimientos")
    void soloIncidenciasNoCreaMantenimientos() throws Exception {
        String token = tokenConSolo("22222222", "INCIDENCIAS");
        String catalogos = "\"idTipoActivo\":" + idTipo + ",\"idUbicacion\":" + idUbicacion;

        // Necesita un activo existente: se crea con un usuario de ACTIVOS.
        String token_activos = tokenConSolo("22222223", "ACTIVOS");
        String cuerpoActivo = mockMvc.perform(post("/api/activos")
                        .header("Authorization", "Bearer " + token_activos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoPatrimonial\":\"SEG-INC-1\",\"nombre\":\"Activo\","
                                + catalogos + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idActivo = idDe(cuerpoActivo);

        // Con su modulo: puede reportar la incidencia.
        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":" + idActivo
                                + ",\"descripcion\":\"Su incidencia\",\"prioridad\":\"BAJA\"}"))
                .andExpect(status().isCreated());

        // Sin el modulo de mantenimientos: no.
        mockMvc.perform(post("/api/mantenimientos/correctivo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idIncidencia\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/mantenimientos/1/finalizar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaFin\":\"2026-01-01T10:00:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario con solo MANTENIMIENTOS no puede tocar incidencias ni evidencias")
    void soloMantenimientosNoTocaLoDemas() throws Exception {
        String token = tokenConSolo("33333333", "MANTENIMIENTOS");

        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":1,\"descripcion\":\"No permitido\",\"prioridad\":\"BAJA\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/evidencias/mantenimiento/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/evidencias/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario con solo EVIDENCIAS no puede tocar el resto")
    void soloEvidenciasNoTocaLoDemas() throws Exception {
        String token = tokenConSolo("44444444", "EVIDENCIAS");
        String catalogos = "\"idTipoActivo\":" + idTipo + ",\"idUbicacion\":" + idUbicacion;

        // Un activo y un mantenimiento reales: el listado de evidencias de un
        // mantenimiento inexistente responde 404, no 200.
        String token_activos = tokenConSolo("44444445", "ACTIVOS");
        String cuerpoActivo = mockMvc.perform(post("/api/activos")
                        .header("Authorization", "Bearer " + token_activos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoPatrimonial\":\"SEG-EVI-1\",\"nombre\":\"Activo\","
                                + catalogos + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idActivo = idDe(cuerpoActivo);

        String token_mantenimientos = tokenConSolo("44444446", "MANTENIMIENTOS");
        String cuerpoMantenimiento = mockMvc.perform(post("/api/mantenimientos/preventivo")
                        .header("Authorization", "Bearer " + token_mantenimientos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":" + idActivo + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idMantenimiento = idDe(cuerpoMantenimiento);

        // Con su modulo: puede leer las evidencias de ese mantenimiento.
        mockMvc.perform(get("/api/evidencias/mantenimiento/" + idMantenimiento)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Sin el modulo de mantenimientos: no.
        mockMvc.perform(post("/api/mantenimientos/preventivo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idActivo\":" + idActivo + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/activos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoPatrimonial\":\"SEG-EVI-2\",\"nombre\":\"Activo\","
                                + catalogos + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Los catalogos se pueden LEER sin su modulo, pero no escribir")
    void catalogosLecturaAbiertaEscrituraCerrada() throws Exception {
        String token = tokenConSolo("55555555", "ACTIVOS");

        // Lectura: abierta a cualquier usuario con sesion.
        mockMvc.perform(get("/api/ubicaciones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tipos-activo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Escritura: exige el modulo del catalogo.
        mockMvc.perform(put("/api/ubicaciones/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"No permitido\",\"tipoUbicacion\":\"SALA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tipos-activo/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario sin USUARIOS no puede ver ni crear usuarios")
    void usuariosExigenSuModulo() throws Exception {
        String token = tokenConSolo("66666666", "ACTIVOS");

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/usuarios/22222222")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un tipo de activo se puede crear sin enviar 'requiereMantenimiento'")
    void tipoActivoAceptaElCampoOmitido() throws Exception {
        // Regresion: con `boolean` primitivo en el record, Jackson exigia el
        // campo y devolvia 400 "El cuerpo de la peticion no es valido".
        // Ahora el campo es opcional y toma el valor por defecto.
        String token = tokenConSolo("77777777", "TIPOS_ACTIVO");

        mockMvc.perform(post("/api/tipos-activo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sin campo booleano\",\"categoria\":\"EQUIPO\"}"))
                .andExpect(status().isCreated());
    }
}
