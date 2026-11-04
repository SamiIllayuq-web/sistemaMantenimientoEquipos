package com.unh.mantenimiento.seguridad.service;

import com.unh.mantenimiento.modulo.Modulo;
import com.unh.mantenimiento.modulo.ModuloRepository;
import com.unh.mantenimiento.modulo.ResponsableModulo;
import com.unh.mantenimiento.seguridad.dto.AuthDtos;
import com.unh.mantenimiento.usuario.model.Responsable;
import com.unh.mantenimiento.usuario.repository.ResponsableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tests del servicio de autenticacion (el "deep module" del modulo de
 * seguridad). Estos tests son el feedback loop principal del desarrollo.
 *
 * Corre contra la BD 'mantenimiento_test' (PostgreSQL real).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private ResponsableRepository responsableRepository;

    @Autowired
    private ModuloRepository moduloRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String DNI = "11111111";
    private static final String CORREO = "juan@unh.edu.pe";
    private static final String CLAVE = "ClaveSegura123";

    @BeforeEach
    void preparar() {
        responsableRepository.deleteAll();

        Responsable responsable = new Responsable();
        responsable.setDni(DNI);
        responsable.setNombres("Juan");
        responsable.setApellidos("Perez");
        responsable.setCorreo(CORREO);
        responsable.setClaveHash(passwordEncoder.encode(CLAVE));
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();
    }

    /** Asigna un modulo al responsable de prueba. */
    private void asignarModulo(String codigo) {
        Modulo modulo = moduloRepository.findByCodigo(codigo).orElseThrow();

        ResponsableModulo asignacion = new ResponsableModulo();
        asignacion.setIdResponsable(DNI);
        asignacion.setIdModulo(modulo.getIdModulo());

        entityManager.persist(asignacion);
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Refresca la entidad desde la BD. Hace flush antes de clear: clear() sin
     * flush descarta los cambios pendientes que aun no llegaron a la BD.
     */
    private void recargar() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Login correcto devuelve token, usuario y modulos")
    void loginCorrecto() {
        asignarModulo("ACTIVOS");

        AuthDtos.LoginResponse respuesta = authService.iniciarSesion(CORREO, CLAVE);

        assertThat(respuesta.token()).isNotBlank();
        assertThat(respuesta.tipoToken()).isEqualTo("Bearer");
        assertThat(respuesta.usuario().dni()).isEqualTo(DNI);
        assertThat(respuesta.usuario().correo()).isEqualTo(CORREO);
        assertThat(respuesta.modulos()).containsExactly("ACTIVOS");
        assertThat(respuesta.expiraEnSegundos()).isPositive();
    }

    @Test
    @DisplayName("La respuesta NUNCA incluye el hash de la clave")
    void nuncaExponeClaveHash() {
        asignarModulo("ACTIVOS");

        AuthDtos.LoginResponse respuesta = authService.iniciarSesion(CORREO, CLAVE);

        assertThat(respuesta.usuario().toString()).doesNotContain("claveHash");
        assertThat(respuesta.usuario().toString()).doesNotContain("$2a$");
        assertThat(respuesta.usuario().toString()).doesNotContain("$2b$");
    }

    @Test
    @DisplayName("Clave incorrecta lanza CredencialesInvalidasException")
    void claveIncorrecta() {
        assertThatThrownBy(() -> authService.iniciarSesion(CORREO, "ClaveIncorrecta"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("Usuario inexistente da el MISMO mensaje que clave incorrecta")
    void usuarioInexistenteMismoMensaje() {
        Throwable porUsuarioInexistente = catchThrowable(
                () -> authService.iniciarSesion("nadie@unh.edu.pe", "CualquierClave1"));
        Throwable porClaveIncorrecta = catchThrowable(
                () -> authService.iniciarSesion(CORREO, "ClaveIncorrecta"));

        assertThat(porUsuarioInexistente).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(porClaveIncorrecta).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(porUsuarioInexistente.getMessage())
                .as("No debe revelar si el usuario existe")
                .isEqualTo(porClaveIncorrecta.getMessage());
    }

    @Test
    @DisplayName("5 intentos fallidos bloquean la cuenta temporalmente")
    void bloqueaTrasCincoIntentos() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.iniciarSesion(CORREO, "ClaveIncorrecta"))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        // Con la cuenta bloqueada, la clave CORRECTA tambien falla.
        assertThatThrownBy(() -> authService.iniciarSesion(CORREO, CLAVE))
                .isInstanceOf(UsuarioBloqueadoException.class);

        Responsable guardado = responsableRepository.findById(DNI).orElseThrow();
        assertThat(guardado.getBloqueadoHasta()).isNotNull();
        assertThat(guardado.getBloqueadoHasta()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Login exitoso limpia el contador de intentos fallidos")
    void loginExitosoLimpiaIntentos() {
        // Dos intentos fallidos primero.
        catchThrowable(() -> authService.iniciarSesion(CORREO, "ClaveIncorrecta"));
        catchThrowable(() -> authService.iniciarSesion(CORREO, "ClaveIncorrecta"));

        entityManager.flush();
        assertThat(responsableRepository.findById(DNI).orElseThrow().getIntentosFallidos())
                .isEqualTo(2);

        // Login correcto.
        authService.iniciarSesion(CORREO, CLAVE);

        recargar();
        Responsable guardado = responsableRepository.findById(DNI).orElseThrow();
        assertThat(guardado.getIntentosFallidos()).isZero();
        assertThat(guardado.getUltimoAcceso()).isNotNull();
    }

    @Test
    @DisplayName("Un usuario INACTIVO no puede iniciar sesion")
    void usuarioInactivoNoPuedeEntrar() {
        Responsable responsable = responsableRepository.findById(DNI).orElseThrow();
        responsable.setEstado("INACTIVO");
        responsableRepository.save(responsable);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> authService.iniciarSesion(CORREO, CLAVE))
                .isInstanceOf(CredencialesInvalidasException.class);
    }
}
