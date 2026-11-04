package com.unh.mantenimiento.seguridad.service;

import com.unh.mantenimiento.modulo.ModuloRepository;
import com.unh.mantenimiento.seguridad.dto.AuthDtos;
import com.unh.mantenimiento.seguridad.model.UsuarioAutenticado;
import com.unh.mantenimiento.usuario.model.Responsable;
import com.unh.mantenimiento.usuario.repository.ResponsableRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de autenticacion. Es un "deep module": una interfaz pequena
 * (iniciarSesion) con toda la logica de intentos, bloqueo y emision de token
 * adentro. El Controller es solo una fachada fina.
 */
@Service
public class AuthService {

    /** Intentos fallidos antes del bloqueo temporal. */
    public static final int MAX_INTENTOS = 5;

    /** Minutos de bloqueo tras agotar los intentos. */
    public static final int MINUTOS_BLOQUEO = 15;

    private final ResponsableRepository responsableRepository;
    private final ModuloRepository moduloRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(ResponsableRepository responsableRepository,
                       ModuloRepository moduloRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.responsableRepository = responsableRepository;
        this.moduloRepository = moduloRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Inicia sesion. Lanza CredencialesInvalidasException si la clave es
     * incorrecta o el usuario no existe (mismo mensaje para ambos casos).
     *
     * IMPORTANTE: noRollbackFor sobre CredencialesInvalidasException. Sin esto,
     * Spring marcaria la transaccion como rollback-only al lanzar la excepcion
     * y el incremento de intentos_fallidos NUNCA se confirmaria: el bloqueo
     * tras 5 intentos jamas se activaria.
     */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public AuthDtos.LoginResponse iniciarSesion(String correo, String clave) {
        Responsable responsable = responsableRepository.findByCorreo(correo).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();

        if (responsable == null) {
            throw new CredencialesInvalidasException();
        }

        if (responsable.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException(responsable.getBloqueadoHasta());
        }

        if (!responsable.esActivo()) {
            throw new CredencialesInvalidasException();
        }

        if (!passwordEncoder.matches(clave, responsable.getClaveHash())) {
            registrarIntentoFallido(responsable, ahora);
            throw new CredencialesInvalidasException();
        }

        // Login exitoso: limpiar intentos y registrar acceso.
        responsable.setIntentosFallidos(0);
        responsable.setBloqueadoHasta(null);
        responsable.setUltimoAcceso(ahora);
        responsableRepository.save(responsable);

        List<String> modulos = responsableRepository.findCodigosModulos(responsable.getDni());
        String token = jwtService.generarToken(responsable.getDni(), responsable.getCorreo(), modulos);

        return AuthDtos.LoginResponse.de(token, responsable, modulos,
                jwtService.getExpiracionMinutos() * 60);
    }

    private void registrarIntentoFallido(Responsable responsable, LocalDateTime ahora) {
        int intentos = responsable.getIntentosFallidos() + 1;
        responsable.setIntentosFallidos(intentos);

        if (intentos >= MAX_INTENTOS) {
            responsable.setBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
            responsable.setIntentosFallidos(0);
        }

        responsableRepository.save(responsable);
    }

    /** Datos del usuario autenticado para construir el Authentication del contexto. */
    @Transactional(readOnly = true)
    public UsuarioAutenticado cargarAutenticado(String dni) {
        Responsable responsable = responsableRepository.findById(dni)
                .orElseThrow(() -> new CredencialesInvalidasException());

        List<String> modulos = responsableRepository.findCodigosModulos(dni);

        return new UsuarioAutenticado(
                responsable.getDni(),
                responsable.getCorreo(),
                responsable.nombreCompleto(),
                responsable.getEstado(),
                modulos.stream()
                        .map(codigo -> "MOD_" + codigo)
                        .toList()
        );
    }
}
