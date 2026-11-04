package com.unh.mantenimiento.seguridad.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Servicio de emision y validacion de JWT.
 * La clave viene de la variable de entorno JWT_SECRET (nunca del codigo).
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMinutos;

    public JwtService(
            @Value("${JWT_SECRET}") String secreto,
            @Value("${JWT_EXPIRACION_MINUTOS:120}") long expiracionMinutos) {
        // HS512 requiere al menos 64 bytes de clave.
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 64 caracteres. "
                            + "Generar uno con: openssl rand -base64 48");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracionMinutos = expiracionMinutos;
    }

    /** Genera un token para el responsable indicado, con sus modulos como permisos. */
    public String generarToken(String dni, String correo, List<String> modulos) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(dni)
                .claim("correo", correo)
                .claim("modulos", modulos)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionMinutos * 60)))
                .signWith(clave)
                .compact();
    }

    /** Extrae los claims del token. Lanza JwtException si es invalido o expiro. */
    public Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean esValido(String token) {
        try {
            parsear(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getDni(String token) {
        return parsear(token).getSubject();
    }

    public long getExpiracionMinutos() {
        return expiracionMinutos;
    }
}
