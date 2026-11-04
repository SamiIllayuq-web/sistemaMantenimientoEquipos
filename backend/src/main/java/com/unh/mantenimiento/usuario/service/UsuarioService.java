package com.unh.mantenimiento.usuario.service;

import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoDuplicadoException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.modulo.Modulo;
import com.unh.mantenimiento.modulo.ModuloRepository;
import com.unh.mantenimiento.modulo.ResponsableModulo;
import com.unh.mantenimiento.usuario.dto.UsuarioDtos;
import com.unh.mantenimiento.usuario.model.Responsable;
import com.unh.mantenimiento.usuario.repository.ResponsableRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de usuarios y permisos. Deep module: interfaz pequena, logica
 * densa adentro (unicidad, modulos obligatorios, proteccion del ultimo
 * administrador).
 */
@Service
public class UsuarioService {

    private static final int LONGITUD_MINIMA_CLAVE = 8;

    private final ResponsableRepository responsableRepository;
    private final ModuloRepository moduloRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public UsuarioService(ResponsableRepository responsableRepository,
                          ModuloRepository moduloRepository,
                          PasswordEncoder passwordEncoder,
                          EntityManager entityManager) {
        this.responsableRepository = responsableRepository;
        this.moduloRepository = moduloRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDtos.UsuarioResponse> listar() {
        return responsableRepository.findAll().stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioDtos.UsuarioResponse buscar(String dni) {
        Responsable responsable = responsableRepository.findById(dni)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con DNI " + dni));
        return aResponse(responsable);
    }

    @Transactional
    public UsuarioDtos.UsuarioResponse crear(UsuarioDtos.CrearUsuarioRequest request) {
        if (responsableRepository.existsByDni(request.dni())) {
            throw new RecursoDuplicadoException("Ya existe un usuario con ese DNI");
        }
        if (responsableRepository.existsByCorreo(request.correo())) {
            throw new RecursoDuplicadoException("Ya existe un usuario con ese correo");
        }
        if (request.clave() == null || request.clave().length() < LONGITUD_MINIMA_CLAVE) {
            throw new IllegalArgumentException(
                    "La contrasena debe tener al menos " + LONGITUD_MINIMA_CLAVE + " caracteres");
        }

        Responsable responsable = new Responsable();
        responsable.setDni(request.dni());
        responsable.setNombres(request.nombres());
        responsable.setApellidos(request.apellidos());
        responsable.setCorreo(request.correo());
        responsable.setClaveHash(passwordEncoder.encode(request.clave()));
        responsable.setTelefono(request.telefono());
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);

        asignarModulos(request.dni(), request.modulos());

        return aResponse(responsable);
    }

    @Transactional
    public UsuarioDtos.UsuarioResponse actualizar(String dni, UsuarioDtos.ActualizarUsuarioRequest request) {
        Responsable responsable = responsableRepository.findById(dni)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con DNI " + dni));

        if (request.correo() != null && !request.correo().equals(responsable.getCorreo())) {
            if (responsableRepository.existsByCorreo(request.correo())) {
                throw new RecursoDuplicadoException("Ya existe un usuario con ese correo");
            }
            responsable.setCorreo(request.correo());
        }

        if (request.nombres() != null) {
            responsable.setNombres(request.nombres());
        }
        if (request.apellidos() != null) {
            responsable.setApellidos(request.apellidos());
        }
        if (request.telefono() != null) {
            responsable.setTelefono(request.telefono());
        }
        if (request.estado() != null) {
            aplicarCambioEstado(responsable, request.estado());
        }

        responsableRepository.save(responsable);

        if (request.modulos() != null) {
            asignarModulos(dni, request.modulos());
        }

        return aResponse(responsable);
    }

    /** Desactiva un usuario. No se borra: los registros con datos relacionados se desactivan. */
    @Transactional
    public UsuarioDtos.UsuarioResponse desactivar(String dni) {
        Responsable responsable = responsableRepository.findById(dni)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con DNI " + dni));

        aplicarCambioEstado(responsable, "INACTIVO");
        responsableRepository.save(responsable);

        return aResponse(responsable);
    }

    /**
     * Cambia el estado. Impide desactivar al ultimo usuario ACTIVO con el
     * modulo USUARIOS (si no, el sistema quedaria sin administracion).
     */
    private void aplicarCambioEstado(Responsable responsable, String nuevoEstado) {
        if ("INACTIVO".equals(nuevoEstado) && "ACTIVO".equals(responsable.getEstado())) {
            Modulo moduloUsuarios = moduloRepository.findByCodigo("USUARIOS").orElse(null);
            if (moduloUsuarios != null) {
                boolean tieneUsuarios = responsableRepository
                        .findCodigosModulos(responsable.getDni()).contains("USUARIOS");
                if (tieneUsuarios) {
                    long activosConUsuarios = responsableRepository
                            .contarActivosConModulo(moduloUsuarios.getIdModulo());
                    if (activosConUsuarios <= 1) {
                        throw new OperacionInvalidaException(
                                "No se puede desactivar al ultimo usuario activo con el modulo USUARIOS");
                    }
                }
            }
        }
        responsable.setEstado(nuevoEstado);
    }

    /** Reemplaza el conjunto de modulos del usuario. */
    private void asignarModulos(String dni, List<String> codigos) {
        if (codigos == null) {
            return;
        }

        // Quitar los modulos actuales.
        List<ResponsableModulo> actuales = entityManager
                .createQuery("""
                        SELECT rm FROM ResponsableModulo rm WHERE rm.idResponsable = :dni
                        """, ResponsableModulo.class)
                .setParameter("dni", dni)
                .getResultList();
        for (ResponsableModulo actual : actuales) {
            entityManager.remove(actual);
        }
        entityManager.flush();

        // Asignar los nuevos.
        for (String codigo : codigos) {
            Modulo modulo = moduloRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el modulo " + codigo));

            ResponsableModulo asignacion = new ResponsableModulo();
            asignacion.setIdResponsable(dni);
            asignacion.setIdModulo(modulo.getIdModulo());
            entityManager.persist(asignacion);
        }
        entityManager.flush();
    }

    private UsuarioDtos.UsuarioResponse aResponse(Responsable responsable) {
        List<String> modulos = responsableRepository.findCodigosModulos(responsable.getDni());
        return new UsuarioDtos.UsuarioResponse(
                responsable.getDni(),
                responsable.getNombres(),
                responsable.getApellidos(),
                responsable.getCorreo(),
                responsable.getTelefono(),
                responsable.getEstado(),
                modulos
        );
    }
}
