package com.unh.mantenimiento.seguridad.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Representacion del usuario autenticado dentro del contexto de Spring
 * Security. Los modulos se traducen a autoridades MOD_XXX.
 */
public class UsuarioAutenticado implements UserDetails {

    private final String dni;
    private final String correo;
    private final String nombreCompleto;
    private final String estado;
    private final List<String> autoridades;

    public UsuarioAutenticado(String dni, String correo, String nombreCompleto,
                               String estado, List<String> autoridades) {
        this.dni = dni;
        this.correo = correo;
        this.nombreCompleto = nombreCompleto;
        this.estado = estado;
        this.autoridades = autoridades;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return autoridades.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        // Nunca se carga la clave_hash en memoria durante la autenticacion por token.
        return null;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"BLOQUEADO".equals(estado);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVO".equals(estado);
    }

    public String getDni() {
        return dni;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getEstado() {
        return estado;
    }

    public List<String> getAutoridades() {
        return autoridades;
    }
}
