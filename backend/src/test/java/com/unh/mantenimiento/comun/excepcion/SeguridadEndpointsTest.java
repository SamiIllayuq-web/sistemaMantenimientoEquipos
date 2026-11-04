package com.unh.mantenimiento.comun.excepcion;

import com.unh.mantenimiento.comun.dto.ErrorResponse;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de seguridad a nivel de endpoint. Verifican que el SERVIDOR valida
 * los permisos por modulo (ocultar el menu en el frontend no es seguridad).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeguridadEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.unh.mantenimiento.usuario.repository.ResponsableRepository responsableRepository;

    @Autowired
    private com.unh.mantenimiento.modulo.ModuloRepository moduloRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Crea un usuario con los modulos indicados, inicia sesion y devuelve
     * el token JWT resultante.
     */
    private String tokenDe(String dni, String correo, String clave, String... modulos) {
        var responsable = new com.unh.mantenimiento.usuario.model.Responsable();
        responsable.setDni(dni);
        responsable.setNombres("Usuario");
        responsable.setApellidos("Prueba");
        responsable.setCorreo(correo);
        responsable.setClaveHash(passwordEncoder.encode(clave));
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(java.time.LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();

        for (String codigo : modulos) {
            var modulo = moduloRepository.findByCodigo(codigo).orElseThrow();
            var asignacion = new com.unh.mantenimiento.modulo.ResponsableModulo();
            asignacion.setIdResponsable(dni);
            asignacion.setIdModulo(modulo.getIdModulo());
            entityManager.persist(asignacion);
        }
        entityManager.flush();
        entityManager.clear();

        try {
            String cuerpo = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}"))
                    .andReturn().getResponse().getContentAsString();
            return extraerToken(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String extraerToken(String json) {
        // El JSON es plano: {"token":"...","tipoToken":"Bearer",...}
        int inicio = json.indexOf("\"token\":\"") + "\"token\":\"".length();
        int fin = json.indexOf('"', inicio);
        return json.substring(inicio, fin);
    }

    @Test
    @DisplayName("Sin token, los endpoints privados devuelven 401")
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ubicaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con token pero SIN el modulo, la escritura devuelve 403")
    void sinModuloEs403() throws Exception {
        String token = tokenDe("33333333", "sinmodulo@unh.edu.pe", "ClaveSegura123", "ACTIVOS");

        mockMvc.perform(post("/api/ubicaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba\",\"tipoUbicacion\":\"SALA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    @DisplayName("Con token pero SIN el modulo, la LECTURA del catalogo SI funciona")
    void lecturaCatalogoSinModulo() throws Exception {
        String token = tokenDe("44444444", "solector@unh.edu.pe", "ClaveSegura123", "ACTIVOS");

        mockMvc.perform(get("/api/ubicaciones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Con el modulo, la escritura funciona")
    void conModuloEs201() throws Exception {
        String token = tokenDe("55555555", "conmodulo@unh.edu.pe", "ClaveSegura123", "UBICACIONES");

        mockMvc.perform(post("/api/ubicaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba 555\",\"tipoUbicacion\":\"SALA\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Sin MOD_USUARIOS, la gestion de usuarios devuelve 403")
    void usuariosSinModuloEs403() throws Exception {
        String token = tokenDe("66666666", "sindirecto@unh.edu.pe", "ClaveSegura123", "ACTIVOS");

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
