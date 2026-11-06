# SEGURIDAD — Sistema de Mantenimiento UNH

Estado: **funcional y verificado por codigos HTTP reales** (104 tests verdes).
Ultima revision: 2026-11-06.

## 1. Que hay aplicado (verificado en codigo)

### Autenticacion
| Control | Donde | Estado |
|---|---|---|
| Contrasenas con BCrypt (fuerza 10) | `SecurityConfig.java:46` | APLICADO |
| Nunca se devuelve `clave_hash` | DTOs de usuario | APLICADO |
| Minimo 8 caracteres al crear usuario | `UsuarioService.java:28` | APLICADO |
| JWT HS512, clave de 64+ bytes, **fuera del codigo** | `JwtService.java:26-38` | APLICADO |
| Sin sesiones (stateless) | `SecurityConfig.java:60` | APLICADO |
| Token expira (120 min configurable) | `JwtService.java:48` | APLICADO |
| Solo usuarios `ACTIVO` pueden iniciar sesion | `AuthService.java:66` | APLICADO |
| 5 intentos fallidos -> bloqueo 15 min | `AuthService.java:24-27` | APLICADO |
| El error de login NO revela si el usuario existe | `AuthService.java:58-60` | APLICADO |

### Autorizacion
| Control | Donde | Estado |
|---|---|---|
| Cada modulo es una autoridad `MOD_XXX` | `ModuloUnh.java:39` | APLICADO |
| **El servidor valida** (`@PreAuthorize`), no solo el menu | todos los controllers | APLICADO |
| Catalogo: lectura abierta, escritura exige su modulo | controllers de catalogo | APLICADO |
| No se puede quitar USUARIOS ni desactivar al ultimo que lo tenga | `UsuarioService` | APLICADO |
| Verificado con codigos reales (401/403/200), no anotaciones | `SeguridadPorModuloTest` (7 tests) | APLICADO |

### Datos e inputs
| Control | Donde | Estado |
|---|---|---|
| Consultas parametrizadas (JPA) | todos los repositories | APLICADO |
| **Cero SQL concatenado** (no hay ni un `nativeQuery`) | grep verificado | APLICADO |
| Bean Validation en las entradas | DTOs con `@Valid` | APLICADO |
| Constraints en BD: CHECK de estados, FK, `valor >= 0`, `fecha_fin >= fecha_inicio` | `V1__esquema_inicial.sql` | APLICADO |
| Sin `innerHTML` / `eval` en el frontend | grep verificado | APLICADO |

### Evidencias (archivos)
| Control | Donde | Estado |
|---|---|---|
| Solo JPG/PNG/PDF, max 10 MB | `EvidenciaUnhService.java:47-54` | APLICADO |
| Content type validado, no solo la extension | `EvidenciaUnhService.java:221` | APLICADO |
| Nombre en disco GENERADO (UUID), nunca el del usuario | `EvidenciaUnhService.java:239` | APLICADO |
| Se sanea el nombre original antes de mostrarlo | `EvidenciaUnhService.java:254` | APLICADO |
| Defensa contra path traversal al descargar | `EvidenciaUnhService.java:161` | APLICADO |
| Todo el modulo (leer/subir/borrar/**descargar**) exige `MOD_EVIDENCIAS` | `EvidenciaUnhController` | APLICADO |
| El archivo NO se sirve como recurso estatico | `SecurityConfig` (`anyRequest().denyAll()`) | APLICADO |

### Red
| Control | Donde | Estado |
|---|---|---|
| CORS solo para `localhost:5174` | `SecurityConfig.java:83` | APLICADO |
| `anyRequest().denyAll()`: nada fuera de `/api/**` | `SecurityConfig.java:68` | APLICADO |
| CSRF desactivado (justificado: API stateless con JWT) | `SecurityConfig.java:58` | APLICADO |

### Secretos
| Control | Estado |
|---|---|
| `.env` en `.gitignore`, **nunca trackeado** | OK (verificado con `git ls-files`) |
| `.env.example` sin valores reales | OK |
| Sin secretos en el historial de git | OK (grep sobre `git log -p --all`) |
| `JWT_SECRET` fuera del codigo, con validacion de 64 bytes | OK |

## 2. Debilidades conocidas (NO son bugs, son limites del alcance de la V1)

1. **Token en `localStorage`** (`frontend/src/api/cliente.js:16`).
   Expuesto a XSS. Mitigacion actual: el frontend no renderiza HTML crudo
   (`dangerouslySetInnerHTML` = 0 ocurrencias). Arreglo definitivo:
   `httpOnly` cookie + CSRF token. Queda para el despliegue.

2. **Sin refresh token.** Al expirar el JWT (120 min) el usuario vuelve a
   iniciar sesion. Es la decision documentada de la V1.

3. **Sin revocacion de token.** Al desactivar un usuario, su JWT sigue
   valido hasta que expira. Mitigacion parcial: `UsuarioAutenticado.isEnabled()`
   es `false` para `INACTIVO`, pero `JwtAuthenticationFilter` **no** lo consulta
   (verificar antes del despliegue).

4. **Sin limite de intentos en la API de recuperacion.** No existe endpoint de
   recuperacion de contrasena, asi que no hay superficie de ataque por ahi.

5. **Contraseña inicial `Admin1234` en el admin del V1** (esta en el codigo de
   la migracion, es un hash bcrypt, no la contrasena en claro). Debe cambiarse
   en el primer ingreso. No hay endpoint de "cambiar mi contrasena".

6. **CORS con `allowCredentials(true)`** y origenes fijos de localhost.
   Sin riesgo en desarrollo; en produccion el origen debe ser el real.

## 3. Pendiente antes de producción

- [ ] Endpoint de "cambiar mi contrasena" (hoy no existe).
- [ ] Verificar si un token de un usuario desactivado sigue sirviendo.
- [ ] `localStorage` -> cookie `httpOnly` + CSRF.
- [ ] CORS con el dominio real + HTTPS.
- [ ] Rate limiting en `/api/auth/login`.
- [ ] Evidencias en Supabase Storage (hoy en el disco del servidor).
- [ ] Backups de la base.

## 4. Como verificar los permisos de verdad

Las anotaciones `@PreAuthorize` no demuestran nada: lo unico que prueba es el
**codigo de estado** que devuelve el servidor.

```bash
# Matriz completa de permisos (401 / 403 / 200 / 404 / 409 / 422)
python3 scripts/verificar_api.py incidencias

# Verificacion de codigos reales por modulo
cd backend && ./mvnw -B test -Dtest=SeguridadPorModuloTest
```

Usuarios de prueba:
- `admin@unh.edu.pe` / `Admin1234` — todos los modulos
- `luis@unh.edu.pe` / `ClaveSegura123` — solo `ACTIVOS`
