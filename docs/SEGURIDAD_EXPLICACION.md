# SEGURIDAD DEL SISTEMA DE MANTENIMIENTO — Guía de lectura del código

> Documento de trabajo personal. Explica **qué seguridad hay aplicada, cómo
> funciona y en qué archivo (con líneas) está cada cosa**, para que puedas
> abrir el código y verificarlo tú mismo.
>
> **Estado**: funcional y verificado con códigos HTTP reales.
> **Fecha**: 2026-11-06 · **Tests**: 104 verdes · **Base de datos**: Supabase
> (schema `mantenimiento`).

---

## 0. Cómo leer este documento

Todo aquí está anclado a un archivo y a un rango de líneas. Cuando digo
"líneas 46-48" me refiero al estado actual del archivo; si mueves código,
los números se desactualizan (úsalos como punto de partida, no como contrato).

Los **8 archivos que contienen el 95% de la seguridad** son:

| # | Archivo | Qué contiene |
|---|---|---|
| 1 | `backend/src/main/java/com/unh/mantenimiento/seguridad/SecurityConfig.java` | Configuración central de Spring Security |
| 2 | `backend/src/main/java/com/unh/mantenimiento/seguridad/service/JwtService.java` | Emisión y validación del JWT |
| 3 | `backend/src/main/java/com/unh/mantenimiento/seguridad/service/AuthService.java` | Login, bloqueo por intentos |
| 4 | `backend/src/main/java/com/unh/mantenimiento/seguridad/service/JwtAuthenticationFilter.java` | Filtro que lee el token de cada petición |
| 5 | `backend/src/main/java/com/unh/mantenimiento/seguridad/model/UsuarioAutenticado.java` | El usuario dentro del contexto de seguridad |
| 6 | `backend/src/main/java/com/unh/mantenimiento/evidencia/service/EvidenciaUnhService.java` | Validación y guardado de archivos |
| 7 | `backend/src/main/java/com/unh/mantenimiento/usuario/service/UsuarioService.java` | Gestión de usuarios y sus permisos |
| 8 | `backend/src/main/java/com/unh/mantenimiento/comun/excepcion/GlobalExceptionHandler.java` | Respuestas de error (no filtra información) |

---

## 1. EL FLUJO COMPLETO: qué pasa cuando haces login y luego pides datos

Esto es lo primero que conviene entender, porque todo lo demás cuelga de ahí.

```
  NAVEGADOR                      SPRING BOOT                        POSTGRESQL
     │                                │                                  │
     │  1. POST /api/auth/login      │                                  │
     │     {correo, clave}           │                                  │
     ├──────────────────────────────►│                                  │
     │                                │ 2. busca el usuario             │
     │                                ├─────────────────────────────────►│
     │                                │ 3. compara clave con BCrypt     │
     │                                │    (NO es la clave en texto)     │
     │                                │ 4. si falla 5 veces -> bloqueo  │
     │  5. {token, usuario, modulos}  │                                  │
     │◄──────────────────────────────┤                                  │
     │                                │                                  │
     │  6. GET /api/activos          │                                  │
     │     Authorization: Bearer xxx │                                  │
     ├──────────────────────────────►│                                  │
     │                         7. JwtAuthenticationFilter:            │
     │                            - saca el token del header           │
     │                            - lo verifica (firma + expiración)   │
     │                            - carga el usuario de la BD         │
     │                            - arma el contexto con MOD_XXX       │
     │                          8. @PreAuthorize: ¿tiene el módulo?   │
     │                            - NO -> 403                          │
     │                            - SI -> ejecuta la consulta          │
     │                                ├─────────────────────────────────►│
     │  9. {contenido: [...], ...}   │                                  │
     │◄──────────────────────────────┤                                  │
```

**Clave del diseño**: el frontend NUNCA decide si algo está permitido.
Ocultar un botón del menú es cortesía visual; lo que manda es la anotación
`@PreAuthorize` en el servidor. Si alguien borra el botón del menú pero
conserva el token, el servidor sigue respondiendo 403.

---

## 2. AUTENTICACIÓN — quién eres

### 2.1 Contraseñas con BCrypt (nunca en texto plano)

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/seguridad/SecurityConfig.java`
**Líneas 45-49**

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

**Qué hace**: BCrypt es una función de derivación de clave. La contraseña se
"cifra" con un algoritmo de un solo sentido (no se puede desencriptar) más una
"sal" aleatoria por usuario. El número 10 es el **coste**: 2^10 = 1024
iteraciones para derivar. Cuanto más alto, más lento el ataque de fuerza
bruta y más lento el login.

**Dónde se guarda el hash**:
`backend/src/main/java/com/unh/mantenimiento/usuario/service/UsuarioService.java`
**línea 78**

```java
responsable.setClaveHash(passwordEncoder.encode(request.clave()));
```

**Dónde se compara**:
`backend/src/main/java/com/unh/mantenimiento/seguridad/service/AuthService.java`
**línea 70**

```java
if (!passwordEncoder.matches(clave, responsable.getClaveHash())) {
```

`matches()` recalcula el hash de la clave que llega y lo compara con el
guardado. Si la base de datos se roba, el atacante ve hashes, no contraseñas.

**Regla de longitud mínima**: `UsuarioService.java` **líneas 28 y 68-71**

```java
private static final int LONGITUD_MINIMA_CLAVE = 8;
...
if (request.clave() == null || request.clave().length() < LONGITUD_MINIMA_CLAVE) {
```

### 2.2 El hash NUNCA sale de la base de datos

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/usuario/service/UsuarioService.java`
**líneas 193-204** — el método `aResponse()` construye el DTO que viaja al
navegador. Mira la lista de campos: `dni, nombres, apellidos, correo, telefono,
estado, modulos`. **No está `claveHash`**. El hash no tiene por dónde salir.

Además, `backend/src/main/java/com/unh/mantenimiento/seguridad/model/UsuarioAutenticado.java`
**líneas 38-42**:

```java
@Override
public String getPassword() {
    // Nunca se carga la clave_hash en memoria durante la autenticacion por token.
    return null;
}
```

Durante la autenticación por token, ni siquiera se carga el hash en memoria.

### 2.3 El JWT (el "token" que te devuelve el login)

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/seguridad/service/JwtService.java`

**La clave secreta sale del entorno, nunca del código** — **líneas 26-37**:

```java
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
```

**Qué es un JWT**: son tres partes separadas por puntos:
`header.payload.firma`. El *payload* lleva los datos del usuario (DNI, correo,
módulos). La *firma* es un HMAC-SHA512 de header+payload hecha con
`JWT_SECRET`. Si alguien modifica el payload, la firma deja de coincidir y el
servidor lo rechaza.

**Por qué 64 bytes**: HS512 usa claves de 512 bits = 64 bytes. Con una clave
más corta la librería lanza una excepción al arrancar — es una protección
contra claves débiles, no un capricho.

**Generación del token** — **líneas 40-51**:

```java
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
```

Ojo al `claim("modulos", modulos)`: los módulos viajan DENTRO del token.
Eso es una decisión de diseño con dos caras — lo bueno es que el servidor no
tiene que consultar la BD en cada petición; lo malo es que si le quitas un
módulo a un usuario, **su token viejo sigue carrying ese módulo** hasta que
expira (ver §7.2).

**Verificación** — **líneas 54-60**:

```java
public Claims parsear(String token) {
    return Jwts.parser()
            .verifyWith(clave)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

`verifyWith(clave)` comprueba la firma. `parseSignedClaims` además valida la
expiración y lanza `JwtException` si el token está vencido o manipulado.

### 2.4 Sin sesiones: el servidor no guarda nada

**Archivo**: `SecurityConfig.java` **líneas 60-61**

```java
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

El servidor no crea sesión HTTP ni cookie de sesión. Cada petición se
autentica sola con su token. Consecuencia: **el token ES la sesión**, y por eso
no hay cierre de sesión real en el servidor (ver §7.2).

### 2.5 Bloqueo por intentos fallidos

**Archivo**: `AuthService.java` **líneas 23-27**

```java
/** Intentos fallidos antes del bloqueo temporal. */
public static final int MAX_INTENTOS = 5;

/** Minutos de bloqueo tras agotar los intentos. */
public static final int MINUTOS_BLOQUEO = 15;
```

**Contador** — **líneas 88-98**:

```java
private void registrarIntentoFallido(ResponsableUnh responsable, LocalDateTime ahora) {
    int intentos = responsable.getIntentosFallidos() + 1;
    responsable.setIntentosFallidos(intentos);

    if (intentos >= MAX_INTENTOS) {
        responsable.setBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
        responsable.setIntentosFallidos(0);
    }

    responsableRepository.save(responsable);
}
```

Al llegar a 5 se fija `bloqueado_hasta = ahora + 15 min` y el contador se
reinicia a 0 (así al desbloquearse hay que 5 intentos nuevos).

**El detalle que casi rompe esto** — **líneas 44-53**:

```java
/**
 * IMPORTANTE: noRollbackFor sobre CredencialesInvalidasException. Sin esto,
 * Spring marcaria la transaccion como rollback-only al lanzar la excepcion
 * y el incremento de intentos_fallidos NUNCA se confirmaria: el bloqueo
 * tras 5 intentos jamas se activaria.
 */
@Transactional(noRollbackFor = CredencialesInvalidasException.class)
public AuthDtos.LoginResponse iniciarSesion(String correo, String clave) {
```

**Por qué importa**: en Spring, si dentro de una transacción `@Transactional`
se lanza una excepción, la transacción se marca para rollback y **se
deshace TODO**, incluido el `UPDATE` del contador. Sin `noRollbackFor`, el
contador nunca se guardaría y el bloqueo de 5 intentos **no existiría** — el
código parecería correcto pero la protección sería falsa. Este es el tipo de
detalle que hace que la prueba valga la pena.

**Comprobación del bloqueo** — **línea 62**:

```java
if (responsable.estaBloqueado(ahora)) {
    throw new UsuarioBloqueadoException(responsable.getBloqueadoHasta());
}
```

### 2.6 El error de login NO revela si el usuario existe

**Archivo**: `AuthService.java` **líneas 58-60**

```java
if (responsable == null) {
    throw new CredencialesInvalidasException();
}
```

Y más abajo, **línea 70**, cuando la clave no coincide, se lanza **la misma**
excepción. Las dos rutas devuelven exactamente el mismo 401 con el mismo
mensaje.

**Por qué**: si "usuario inexistente" y "clave incorrecta" devolvieran
mensajes distintos, un atacante podría enumerar qué correos existen en el
sistema (reconocimiento de usuarios). Es una práctica estándar (OWASP).

### 2.7 Solo los usuarios activos pueden iniciar sesión

**Archivo**: `AuthService.java` **línea 66**

```java
if (!responsable.esActivo()) {
    throw new CredencialesInvalidasException();
}
```

Y la definición de "activo", en
`backend/src/main/java/com/unh/mantenimiento/usuario/model/ResponsableUnh.java`
**líneas 63-65**:

```java
public boolean esActivo() {
    return "ACTIVO".equals(estado);
}
```

Verificado empíricamente: con el usuario desactivado, el login devuelve **401**.

---

## 3. AUTORIZACIÓN — qué puedes hacer

### 3.1 Los módulos son autoridades de Spring Security

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/modulo/ModuloUnh.java`
**líneas 38-41**

```java
/** La autoridad de Spring Security que exige este modulo. */
public String autoridad() {
    return "MOD_" + codigo;
}
```

Así, el módulo `ACTIVOS` se convierte en la autoridad `MOD_ACTIVOS`, y el
módulo `EVIDENCIAS` en `MOD_EVIDENCIAS`. Spring Security ya sabe evaluar
"¿tiene esta autoridad?".

### 3.2 Traducción de usuario → autoridades

**Archivo**: `AuthService.java` **líneas 101-117**

```java
@Transactional(readOnly = true)
public UsuarioAutenticado cargarAutenticado(String dni) {
    ResponsableUnh responsable = responsableRepository.findById(dni)
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
```

Ojo: los módulos se leen **de la base de datos**, no del token. El claim
`modulos` del token es informativo; lo que manda es esta consulta. (Salvo que
el token ya vine con `MOD_XXX` dentro… pero el filtro reemplaza las
autoridades por las de la BD, ver §3.4.)

### 3.3 El filtro: qué pasa en CADA petición

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/seguridad/service/JwtAuthenticationFilter.java`
**líneas 35-63**

```java
@Override
protected void doFilterInternal(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull FilterChain filterChain)
        throws ServletException, IOException {

    String token = extraerToken(request);

    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        try {
            Claims claims = jwtService.parsear(token);
            String dni = claims.getSubject();

            UsuarioAutenticado usuario = authService.cargarAutenticado(dni);

            UsernamePasswordAuthenticationToken autenticacion =
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } catch (Exception e) {
            // Token invalido o expirado: se sigue la cadena sin autenticar.
            // El entry point devolvera 401.
            SecurityContextHolder.clearContext();
        }
    }

    filterChain.doFilter(request, response);
}
```

Lectura paso a paso:
1. **Línea 41**: saca el token del header `Authorization`.
2. **Línea 45**: lo verifica (firma + expiración). Si falla, salta al catch.
3. **Línea 48**: **vuelve a la base de datos** a cargar el usuario. Esto es
   importante: el estado del usuario se consulta en cada petición.
4. **Línea 50-51**: mete al usuario en el contexto de seguridad con sus
   autoridades.
5. **Línea 55-59**: si algo falla, limpia el contexto. La petición sigue su
   camino **sin autenticar**, y yaISD la cadena de seguridad la que
   responderá 401.

Extracción del header — **líneas 65-71**:

```java
private String extraerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");   // línea 66
    if (header != null && header.startsWith(PREFIJO)) {
        return header.substring(PREFIJO.length()).trim();
    }
    return null;
}
```

`PREFIJO = "Bearer "` (línea 25).

### 3.4 Qué rutas existen y qué exigen

**Archivo**: `SecurityConfig.java` **líneas 62-68**

```java
.authorizeHttpRequests(auth -> auth
        // Publicos: login y catalogo de modulos.
        .requestMatchers("/api/auth/login").permitAll()
        .requestMatchers("/api/modulos").permitAll()
        // Cualquier otro endpoint exige sesion + su modulo.
        .requestMatchers("/api/**").authenticated()
        .anyRequest().denyAll())
```

Cuatro niveles, de menos a más restrictivo:

| Regla | Efecto |
|---|---|
| `/api/auth/login` → `permitAll()` | Público: es la puerta de entrada |
| `/api/modulos` → `permitAll()` | Público: el frontend necesita los módulos para armar el menú antes de login |
| `/api/**` → `authenticated()` | Todo lo demás exige token válido |
| `anyRequest()` → `denyAll()` | **Nada fuera de `/api/**`**. Ni archivos estáticos, ni rutas desconocidas |

**`denyAll()` es importante**: sin él, cualquier ruta no mapeada (por ejemplo
`/actuator`, `/h2-console`, o un archivo del directorio de evidencias) quedaría
accesible. Con `denyAll()`, la superficie expuesta es exactamente `/api/**`.

### 3.5 El permiso por módulo, endpoint por endpoint

Estos son TODOS los `@PreAuthorize` del proyecto. Cada uno exige una
autoridad concreta:

| Archivo | Línea | Endpoint | Exige |
|---|---|---|---|
| `activo/controller/ActivoUnhController.java` | 71 | `POST /api/activos` | `MOD_ACTIVOS` |
| `activo/controller/ActivoUnhController.java` | 78 | `PUT /api/activos/{id}` | `MOD_ACTIVOS` |
| `activo/controller/ActivoUnhController.java` | 87 | `PUT .../desactivar` | `MOD_ACTIVOS` |
| `activo/controller/ActivoUnhController.java` | 94 | `DELETE ...` | `MOD_ACTIVOS` |
| `incidencia/controller/IncidenciaUnhController.java` | 74 | `POST /api/incidencias` | `MOD_INCIDENCIAS` |
| `incidencia/controller/IncidenciaUnhController.java` | 82 | `PUT ...` | `MOD_INCIDENCIAS` |
| `mantenimiento/controller/MantenimientoUnhController.java` | 74 | `POST /api/mantenimientos/correctivo` | `MOD_MANTENIMIENTOS` |
| `mantenimiento/controller/MantenimientoUnhController.java` | 83 | `POST .../preventivo` | `MOD_MANTENIMIENTOS` |
| `mantenimiento/controller/MantenimientoUnhController.java` | 92 | `PUT /api/mantenimientos/{id}` | `MOD_MANTENIMIENTOS` |
| `mantenimiento/controller/MantenimientoUnhController.java` | 101 | `PUT .../finalizar` | `MOD_MANTENIMIENTOS` |
| `evidencia/controller/EvidenciaUnhController.java` | 52 | `GET /api/evidencias/mantenimiento/{id}` | `MOD_EVIDENCIAS` |
| `evidencia/controller/EvidenciaUnhController.java` | 67 | `POST /api/evidencias/mantenimiento/{id}` | `MOD_EVIDENCIAS` |
| `evidencia/controller/EvidenciaUnhController.java` | 86 | `GET /api/evidencias/{id}/descargar` | `MOD_EVIDENCIAS` |
| `evidencia/controller/EvidenciaUnhController.java` | 103 | `DELETE /api/evidencias/{id}` | `MOD_EVIDENCIAS` |
| `usuario/controller/UsuarioController.java` | 27 (a nivel de clase) | TODO `/api/usuarios` | `MOD_USUARIOS` |
| `ubicacion/controller/UbicacionUnhController.java` | 48, 55, 63 | escritura de ubicaciones | `MOD_UBICACIONES` |
| `tipoactivo/controller/TipoActivoUnhController.java` | 52, 59, 67 | escritura de tipos | `MOD_TIPOS_ACTIVO` |

**Los catálogos se pueden LEER sin su módulo.** Solo la escritura lo exige.
Mira que en `UbicacionUnhController.java` y `TipoActivoUnhController.java` los
`@PreAuthorize` están en los métodos de escritura, no en el de lectura. Es una
decisión de negocio: todos los usuarios necesitan ver dónde están los activos,
pero solo quien tiene el módulo puede crear o modificar catálogos.

**Casos sin `@PreAuthorize` a propósito:**
- `AuthController.java` línea 31 (`POST /api/auth/login`) → público.
- `AuthController.java` línea 38 (`GET /api/auth/me`) → exige sesión, ya
  garantizada por `/api/**`.
- `ModuloUnhController.java` línea 23 (`GET /api/modulos`) → público.

**Para activar la seguridad por método** hace falta el `@EnableMethodSecurity`
de `SecurityConfig.java` **línea 32**. Sin esa anotación, los `@PreAuthorize`
serían texto decorativo: Spring los ignoraría por completo.

### 3.6 El cuerpo de la petición se valida ANTES que el permiso

Esto es un detalle de Spring que conviene conocer, porque genera confusiones
al probar con `curl`.

Orden real de ejecución:
1. Spring valida el cuerpo con Bean Validation (`@Valid`) → si falla, **400**.
2. Spring evalúa el `@PreAuthorize` → si falla, **403**.

**Consecuencia práctica**: si pruebas con un JSON incompleto, verás **400**
aunque tu usuario no tenga permiso. Si pruebas con un JSON válido, verás
**403** (el permiso). Con `curl`, siempre usa un payload **válido** para
comprobar permisos; si no, concluirás que el permiso no funciona cuando el
problema era el body.

### 3.7 No se puede quedar el sistema sin administración

**Archivo**: `UsuarioService.java` **líneas 142-159**

```java
private void aplicarCambioEstado(ResponsableUnh responsable, String nuevoEstado) {
    if ("INACTIVO".equals(nuevoEstado) && "ACTIVO".equals(responsable.getEstado())) {
        ModuloUnh moduloUsuarios = moduloRepository.findByCodigo("USUARIOS").orElse(null);
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
```

Impide que el último administrador activo se desactive a sí mismo y deje el
sistema sin nadie que pueda gestionarlo. Sale como **422** (regla de negocio
violada), no como 400.

### 3.8 Correo y DNI únicos

**Archivo**: `UsuarioService.java`
- **líneas 63-66** (al crear): comprueba DNI duplicado → `RecursoDuplicadoException` (409) y correo duplicado → 409.
- **líneas 96-101** (al actualizar): si cambia el correo, vuelve a comprobar que no esté repetido.

**La base de datos también lo garantiza** (no solo la aplicación):
`backend/src/main/resources/db/migration/V1__esquema_inicial.sql`
**línea 15** → `correo VARCHAR(150) NOT NULL UNIQUE`.

---

## 4. ARCHIVOS SUBIDOS (evidencias) — la parte más delicada

Las evidencias son archivos que sube el usuario y que después se descargan.
Aquí hay más superficie de ataque que en el resto del sistema.

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/evidencia/service/EvidenciaUnhService.java`

### 4.1 Solo JPG, PNG y PDF, máximo 10 MB

**líneas 46-54**

```java
/** Tamano maximo permitido: 10 MB. */
public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

/** Extensiones permitidas. */
private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "pdf");

/** Content types aceptados, para no confiar solo en la extension. */
private static final Set<String> TIPOS_PERMITIDOS = Set.of(
        "image/jpeg", "image/jpg", "image/png", "application/pdf");
```

### 4.2 Se valida el contenido, no solo la extensión

**líneas 205-225**

```java
private void validarArchivo(MultipartFile archivo) {
    if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
        throw new OperacionInvalidaException(
                "El archivo supera el tamano maximo de 10 MB");
    }

    String nombre = archivo.getOriginalFilename();
    String extension = extensionDe(nombre);

    if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
        throw new OperacionInvalidaException(
                "Tipo de archivo no permitido. Solo se aceptan JPG, PNG y PDF");
    }

    String tipo = archivo.getContentType();
    if (tipo != null && !TIPOS_PERMITIDOS.contains(tipo.toLowerCase(Locale.ROOT))) {
        throw new OperacionInvalidaException(
                "El contenido del archivo no coincide con un JPG, PNG o PDF");
    }
}
```

**Por qué las dos comprobaciones**: la extensión la pone el navegador y se
puede falsear. Un atacante podría mandar `malware.exe` renombrado a
`malware.png` — la extensión pasaría, pero el content type del servidor
(`application/x-msdownload`) no. Con las dos comprobaciones, tiene que
engañar a las dos.

*(Limitación honesta: esto valida el tipo declarado, no el contenido real del
archivo. Un PDF con un script dentro pasaría el filtro. Para eso harían falta
análisis antivirus o sandbox.)*

### 4.3 El nombre en disco NUNCA es el del usuario

**líneas 237-240**

```java
/** Nombre generado: nunca el del usuario. */
private String generarNombre(String nombreOriginal) {
    return UUID.randomUUID() + extensionConPunto(extensionDe(nombreOriginal));
}
```

El nombre que manda el usuario se guarda solo como texto para mostrarlo
(`nombre_original`), y **se sanea** — **líneas 242-262**:

```java
private String sanearNombreOriginal(String nombreOriginal) {
    if (nombreOriginal == null || nombreOriginal.isBlank()) {
        return "archivo";
    }
    // Se quita la ruta que algunos navegadores agregan y los separadores.
    String limpio = nombreOriginal.replace('\\', '/');
    int ultimaBarra = limpio.lastIndexOf('/');
    if (ultimaBarra >= 0) {
        limpio = limpio.substring(ultimaBarra + 1);
    }
    limpio = limpio.replaceAll("[^A-Za-z0-9._-]", "_");
    ...
}
```

**Por qué**: con un UUID, es imposible que el usuario se provoque un
nombre como `../../../etc/passwd` o que dos archivos se pisen entre sí.

### 4.4 Defensa contra path traversal al descargar

**líneas 157-163**

```java
Path ruta = carpeta.resolve(evidencia.getNombreArchivo()).normalize();

// Defensa extra: el nombre generado nunca debe salir de la carpeta.
if (!ruta.startsWith(carpeta)) {
    throw new OperacionInvalidaException("Ruta de archivo no valida");
}
```

`normalize()` resuelve los `..` y el `startsWith` comprueba que la ruta
resuelta siga dentro de la carpeta de evidencias. Aunque el nombre almacenado
fuera malicioso, no puede salirse.

### 4.5 Toda la descarga exige sesión Y módulo

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/evidencia/controller/EvidenciaUnhController.java`
**líneas 84-99**

```java
/** Descarga el archivo de una evidencia. */
@GetMapping("/{id}/descargar")
@PreAuthorize("hasAuthority('MOD_EVIDENCIAS')")
public ResponseEntity<Resource> descargar(@PathVariable Integer id) {
```

El **descargar** también exige `MOD_EVIDENCIAS`. Es una decisión
deliberadamente estricta (a diferencia de los catálogos, donde leer es
abierto): una evidencia es material de un trabajo de mantenimiento, no un
dato de catálogo.

### 4.6 Los archivos NO se sirven como recurso estático

La carpeta de evidencias (`EVIDENCIAS_DIR`, por defecto
`/home/saminillayuq/evidencias`) está **fuera** del directorio del proyecto y
fuera de cualquier ruta servida por Spring. El único camino para leer un
archivo es el endpoint con `@PreAuthorize`. Combinado con
`anyRequest().denyAll()` (§3.4), no hay forma de pedir el archivo por URL
directa.

---

## 5. BASE DE DATOS — qué protege el motor

### 5.1 Consultas parametrizadas (cero SQL concatenado)

Todo el acceso a datos va por Spring Data JPA, que genera consultas
parametrizadas (`?` binds). **Verificado: el proyecto no contiene ni un solo
`nativeQuery`**, así que no existe ninguna consulta SQL escrita a mano donde
se pueda concatenar entrada del usuario.

Es la defensa contra **SQL injection**: un usuario que escribiera
`'; DROP TABLE activo_unh; --` en un campo de texto se trata como texto
literal, no como SQL.

### 5.2 Las restricciones viven en la base de datos

**Archivo**: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`

No todo se valida en Java. La base de datos es la última línea de defensa:

| Línea | Restricción | Qué evita |
|---|---|---|
| 17 | `CHECK (estado IN ('ACTIVO','INACTIVO'))` | Estados inventados |
| 42, 44 | `REFERENCES ... ON DELETE CASCADE` | Huérfanos y borrados inconsistentes |
| 66 | `CHECK (categoria IN ('EQUIPO','INFRAESTRUCTURA'))` | Categorías inválidas |
| 87 | `CHECK (estado_operativo IN (...))` | Estado operativo inválido |
| 96 | `CHECK (valor_adquisicion IS NULL OR valor_adquisicion >= 0)` | Valor de adquisición negativo |
| 108 | `CHECK (prioridad_incidencia IN ('ALTA','MEDIA','BAJA'))` | Prioridad inventada |
| 147 | `CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)` | Mantenimiento que termina antes de empezar |
| 149 | `CHECK (tipo_mantenimiento = 'PREVENTIVO' OR id_incidencia IS NOT NULL)` | Un correctivo sin incidencia |
| 162 | `CHECK (tipo_archivo IN ('FOTO','INFORME','OTRO'))` | Tipo de archivo inválido |

La última de la lista es elegante: garantiza en la BD que **todo
mantenimiento correctivo tiene incidencia**, sin importar qué ruta del código
lo haya creado.

### 5.3 La aplicación NUNCA modifica el esquema

**Archivo**: `backend/src/main/resources/application.properties` **línea 33**

```properties
spring.jpa.hibernate.ddl-auto=validate
```

`validate` significa: "comprueba que el esquema de la BD coincide con mis
entidades y **falla si no coincide**". La app no puede crear ni alterar
tablas. Los cambios de esquema pasan SIEMPRE por una migración de Flyway
(`backend/src/main/resources/db/migration/`), que queda registrada en la tabla
`flyway_schema_history`. Eso permite auditar exactamente qué cambió y cuándo.

### 5.4 El repositorio de PostgreSQL es COMPARTIDO

**Por qué importa para la seguridad**: el servidor PostgreSQL de esta máquina
lo usan también el sistema de cobranza, SIGAI y el de admisión. Por eso
todas las tablas de este sistema llevan el sufijo `_unh` y las clases Java el
sufijo `Unh`. Sin ese sufijo, una tabla `activo` genérica podría pisar la de
otro proyecto — un accidente de datos que es también un problema de
aislamiento.

---

## 6. RED Y SESIÓN (frontend)

### 6.1 CORS limitado al frontend de desarrollo

**Archivo**: `SecurityConfig.java` **líneas 83-97**

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuracion = new CorsConfiguration();
    configuracion.setAllowedOrigins(List.of(
            "http://localhost:5174",
            "http://127.0.0.1:5174"
    ));
    configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuracion.setExposedHeaders(List.of("Content-Disposition"));
    configuracion.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/api/**", configuracion);
    return fuente;
}
```

**Qué es CORS**: la regla del navegador que impide que una página de
`otro-sitio.com` haga peticiones a tu API. Aquí solo se permite el origen
del frontend de desarrollo.

**En la práctica, CORS ni siquiera aplica en desarrollo**: Vite hace proxy de
`/api` hacia `localhost:8081` (está en `frontend/vite.config.js`), así que
el navegador ve siempre el mismo origen. El CORS está configurado para el
caso de que el frontend se sirva desde otro origen.

### 6.2 CSRF desactivado — y por qué es correcto aquí

**Archivo**: `SecurityConfig.java` **línea 58**

```java
.csrf(csrf -> csrf.disable())
```

CSRF es un ataque donde una página maliciosa hace que tu navegador envíe
una petición autenticada sin que tú lo sepas. La protección estándar es una
cookie + token.

**Por qué se puede desactivar**: el CSRF existe para abusar de la sesión
automática vía cookies. Aquí **no hay cookies de sesión**: la autenticación
va en el header `Authorization`, y un navegador solo envía ese header si la
petición pasa por JavaScript del mismo origen… y el CORS (§6.1) impide que
otra página lo haga. Sin cookies, el ataque clásico no aplica.

*(Precaución: si en el futuro se cambia a cookies `httpOnly`, hay que
reactivar CSRF. Está anotado en §7.)*

### 6.3 El token vive en `localStorage`

**Archivo**: `frontend/src/api/cliente.js` **líneas 14-22**

```javascript
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

**Limpieza automática al expirar** — **líneas 26-34**:

```javascript
(error) => {
  const estado = error.response?.status
  if (estado === 401 && window.location.pathname !== '/login') {
    localStorage.removeItem('token')
    localStorage.removeItem('usuario')
    localStorage.removeItem('modulos')
    window.location.href = '/login'
  }
  return Promise.reject(error)
}
```

Cualquier 401 (token expirado o inválido) limpia la sesión y manda al login.

**Riesgo conocido**: `localStorage` es accesible desde cualquier JavaScript
que se ejecute en la página. Si alguna vez hubiera un XSS, el atacante se
lleva el token. Hoy está mitigado porque **el frontend no renderiza HTML
crudo**: verificado, hay **cero** usos de `dangerouslySetInnerHTML`, `innerHTML`
o `eval()` en todo `frontend/src`. La solución definitiva (cookie `httpOnly`
+ CSRF) está en §7.

---

## 7. LO QUE NO ESTÁ APLICADO (lo debes saber antes de producción)

### 7.1 Token en `localStorage` en vez de cookie `httpOnly`
Detalle en §6.3. **Prioridad: media.** Se arregla al desplegar.

### 7.2 Sin revocación de token — la más importante
**Verificado empíricamente**: desactivé al usuario `luis` y **su token ya
emitido siguió funcionando** (`GET /api/activos` devolvió **200**). El login
nuevo sí falla (401).

**Causa**: `JwtAuthenticationFilter.java` **línea 48** llama a
`authService.cargarAutenticado(dni)`, que carga el estado del usuario en la
línea 112 de `AuthService.java`… pero ese estado **nunca se comprueba**.

Existe la comprobación, pero está donde no surte efecto:
`UsuarioAutenticado.java` **líneas 64-66**
```java
@Override
public boolean isEnabled() {
    return "ACTIVO".equals(estado);
}
```

Spring Security *podría* usarla, pero `JwtAuthenticationFilter` construye el
`UsernamePasswordAuthenticationToken` **sin llamar a `isEnabled()`**
(línea 50-51 del filtro), así que el usuario desactivado se cuela.

**El arreglo sería una línea**: en el filtro, si `!usuario.isEnabled()`, hacer
`SecurityContextHolder.clearContext()` (y devolver 401).

**Ventana de exposición**: hasta 120 minutos (el expiry por defecto del
token). Con `JWT_EXPIRACION_MINUTOS` menor, se reduce.

**Prioridad: ALTA.** Es la que arreglaría yo primero.

### 7.3 No existe "cambiar mi contraseña"
Los endpoints de usuario (`UsuarioController.java` líneas 36, 41, 46, 52, 60)
son listar, ver, crear, actualizar y desactivar. **Ninguno cambia la
contraseña**. El administrador inicial se creó con `Admin1234` (el hash está
en `V1__esquema_inicial.sql` línea 203) y **no se puede cambiar desde la
aplicación**.

**Prioridad: ALTA** para un sistema real.

### 7.4 Sin rate limiting
El bloqueo de 5 intentos (§2.5) está en la **base de datos** y es por
**usuario**. Un atacante puede probar infinitas contraseñas contra muchos
usuarios distintos sin toparse con el bloqueo, porque cada usuario tiene su
propio contador. Un rate limit en `/api/auth/login` (por IP) lo cerraría.

### 7.5 Evidencias en el disco del servidor
Hoy `EVIDENCIAS_DIR` es una carpeta en el disco. Si el despliegue es en un
servidor único, está bien. Si hay varias instancias, cada una tendría su
propia carpeta (y un archivo subido en una instancia no se vería en otra).
En producción: Supabase Storage o un volumen compartido.

### 7.6 Contraseña de la base de datos en el `.env`
Está fuera del repositorio (`.gitignore` línea 2), pero es una contraseña
en texto plano en un archivo de la máquina. Para producción conviene un gestor
de secretos.

---

## 8. ERRORES: qué información se filtra (o no)

**Archivo**: `backend/src/main/java/com/unh/mantenimiento/comun/excepcion/GlobalExceptionHandler.java`

Cada tipo de error tiene su handler, y el **código HTTP y el mensaje son
exactos**:

| Línea | Excepción | Código | Mensaje que ve el usuario |
|---|---|---|---|
| 24-28 | `CredencialesInvalidasException` | **401** | Credenciales inválidas (no dice cuál falló) |
| 30-34 | `UsuarioBloqueadoException` | **423** | Usuario bloqueado temporal |
| 37-46 | `MethodArgumentNotValidException` | **400** | Errores campo por campo |
| 48-52 | `HttpMessageNotReadableException` | **400** | Cuerpo ilegible |
| 54-58 | `IllegalArgumentException` | **400** | Mensaje del argumento |
| 60-64 | `RecursoNoEncontradoException` | **404** | "No existe X con ID N" |
| 66-70 | `RecursoDuplicadoException` | **409** | "Ya existe…" |
| 72-76 | `OperacionInvalidaException` | **422** | Regla de negocio violada |
| 84-89 | `AuthorizationDeniedException` | **403** | "No tienes permiso para realizar esta accion" |
| 91-96 | `AccessDeniedException` | **403** | "No tienes permiso para realizar esta accion" |
| 98-103 | `Exception` (genérico) | **500** | "Ocurrio un error inesperado en el servidor" |

**Lo relevante del último**: el handler genérico **no** imprime el mensaje
de la excepción interna. Si una SQLException sale a la luz, el usuario ve
"Ocurrió un error inesperado", no el SQL ni los nombres de tablas. El detalle
real va al log del servidor, no al navegador.

**Los 403 (líneas 84-96) no dicen qué módulo falta.** Dicen "no tienes
permiso", sin revelar la estructura interna de permisos.

---

## 9. SECRETOS Y CONFIGURACIÓN

### 9.1 Qué hay en `.env` y por qué no está en git

**Archivo**: `.env` (raíz del repo) — está en `.gitignore` (línea 2: `.env`,
línea 3: `.env.local`, línea 4: `*.env`).

Verificado: `git ls-files` **no** lista `.env`. Solo `.env.example` está
trackeado, y no tiene valores reales.

### 9.2 El `.env` se carga solo

**Archivo**: `backend/src/main/resources/application.properties` **líneas 33-37**

```properties
spring.config.import=optional:file:${user.dir}/../.env[.properties],optional:file:./.env[.properties]
```

`optional` = si el archivo no existe, la app arranca igual con los valores por
defecto. Se buscan dos rutas para funcionar tanto desde `backend/` como desde
la raíz del repo.

**¿Por qué `JWT_SECRET` con `${...}` y no con valor por defecto?** Porque en Spring los
valores por defecto se escriben `${CLAVE:valor_por_defecto}`. Para
`JWT_SECRET` **no** hay valor por defecto a propósito: si falta el `.env`, la
app debe **fallar al arrancar** en vez de arrancar con una clave conocida por
todo el mundo. Por eso `JwtService.java` línea 27 usa
`@Value("${JWT_SECRET}")` sin valor por defecto, y la línea 31 valida que tenga 64 bytes.

### 9.3 Sin secretos en el historial de git

Verificado sobre `git log -p --all`: no hay contraseñas, tokens ni claves
escritas en ningún commit.

### 9.4 La configuración de Supabase

**Archivo**: `.env` — el proyecto usa el **pooler de SESIÓN** (puerto 5432):

```
DB_URL=jdbc:postgresql://aws-1-us-west-1.pooler.supabase.com:5432/postgres
DB_USER=postgres.nvfbppvseevfiagqmtgb
DB_PASSWORD=<la clave de la base>
DB_SCHEMA=mantenimiento
```

**Por qué el pooler de sesión y no el de transacción (6543)**: Hibernate usa
*prepared statements* del lado del servidor. El pooler de transacción los
descarta entre transacciones, y la app falla con
`prepared statement ... does not exist`. Confirmado en la documentación
oficial de Supabase.

**Por qué un schema propio (`mantenimiento`) y no `public`**: el schema
`public` de Supabase **ya tiene objetos** (los de la propia plataforma), y
Flyway se niega a migrar un schema no vacío sin un *baseline*. Con un schema
propio, Flyway lo crea limpio y aplica las migraciones sin tocar nada ajeno.

---

## 10. CÓMO VERIFICAR TÚ MISMO QUE FUNCIONA

Las anotaciones `@PreAuthorize` **no demuestran** que el permiso funcione.
Lo único que lo demuestra es el **código de estado** que devuelve el servidor.

### 10.1 La prueba automática

```bash
cd backend && ./mvnw -B test -Dtest=SeguridadPorModuloTest
```

**Archivo**: `backend/src/test/java/com/unh/mantenimiento/comun/excepcion/SeguridadPorModuloTest.java`
(363 líneas, 7 tests). Comprueba, con usuarios reales de un solo módulo:

| Test (línea) | Qué comprueba |
|---|---|
| 144-154 | Sin token, **todos** los endpoints privados devuelven 401 |
| 156-203 | Un usuario con solo `ACTIVOS` **no** puede escribir en los demás módulos |
| 205-242 | Un usuario con solo `INCIDENCIAS` no puede crear mantenimientos |
| 244-262 | Un usuario con solo `MANTENIMIENTOS` no toca incidencias ni evidencias |
| 264-309 | Un usuario con solo `EVIDENCIAS` no toca el resto |
| 311-333 | Los catálogos se **leen** sin su módulo, pero no se **escriben** |
| 335-347 | Sin `USUARIOS` no se ven ni se crean usuarios |

### 10.2 Verificación a mano

```bash
# 1. Conseguir un token (admin tiene los 7 módulos)
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"correo":"admin@unh.edu.pe","clave":"Admin1234"}'

# 2. Sin token -> 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/activos

# 3. Con token -> 200
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer <TOKEN>" http://localhost:8081/api/activos
```

**Ojo (§3.6)**: para probar un 403, el body debe ser **válido**. Con un body
incompleto obtendrás 400 y pensarás que el permiso falla.

### 10.3 Los usuarios de prueba

| Correo | Clave | Módulos |
|---|---|---|
| `admin@unh.edu.pe` | `Admin1234` | los 7 |
| `luis@unh.edu.pe` | `ClaveSegura123` | solo `ACTIVOS` |

Con `luis` puedes comprobar tú mismo el 403: entra en el frontend, entra a
Incidencias e intenta reportar una → el servidor responde 403 aunque el
frontend no muestre el botón.

---

## 11. RESUMEN EN UNA TABLA

| Área | Control | Verificado |
|---|---|---|
| **Autenticación** | BCrypt fuerza 10 | ✅ |
| | Hash nunca sale de la BD | ✅ |
| | Mínimo 8 caracteres | ✅ |
| | JWT HS512, clave de 64+ bytes del entorno | ✅ |
| | Stateless (sin sesiones) | ✅ |
| | Expiración de 120 min | ✅ |
| | Solo usuarios activos pueden entrar | ✅ |
| | 5 intentos → bloqueo 15 min | ✅ |
| | El error no revela si el usuario existe | ✅ |
| **Autorización** | Permisos por módulo validados por el servidor | ✅ |
| | `anyRequest().denyAll()` | ✅ |
| | Catálogos: lectura abierta, escritura cerrada | ✅ |
| | No se puede quedar sin administrador | ✅ |
| **Datos** | Cero SQL concatenado | ✅ |
| | Constraints CHECK en la BD | ✅ |
| | `ddl-auto=validate` (la app no altera el esquema) | ✅ |
| | Sufijo `_unh` (aisla de otros proyectos en el mismo Postgres) | ✅ |
| **Archivos** | Solo JPG/PNG/PDF, 10 MB, content type validado | ✅ |
| | Nombre UUID en disco | ✅ |
| | Defensa de path traversal | ✅ |
| | Descarga exige `MOD_EVIDENCIAS` | ✅ |
| | No se sirven como recurso estático | ✅ |
| **Red** | CORS limitado a los orígenes del frontend | ✅ |
| | CSRF desactivado (justificado: sin cookies) | ✅ |
| **Secretos** | `.env` fuera de git | ✅ |
| | Sin secretos en el historial | ✅ |
| | `.env` cargado sin `source` | ✅ |
| **Pendiente** | Token en `localStorage` (XSS) | ⚠️ |
| | **Sin revocación de token** | ❌ |
| | **Sin "cambiar mi contraseña"** | ❌ |
| | Sin rate limiting en login | ⚠️ |
| | Evidencias en disco (no en Storage) | ⚠️ |

---

## 12. ARCHIVOS COMPLETOS (mapa para navegar)

```
backend/src/main/java/com/unh/mantenimiento/
├── seguridad/
│   ├── SecurityConfig.java              ← toda la config de Spring Security
│   ├── controller/AuthController.java    ← login y /me
│   ├── model/UsuarioAutenticado.java     ← el usuario en el contexto
│   └── service/
│       ├── AuthService.java              ← intentos, bloqueo, emisión
│       ├── JwtService.java               ← firma y verificación del token
│       └── JwtAuthenticationFilter.java  ← se ejecuta en cada petición
├── usuario/
│   ├── controller/UsuarioController.java ← @PreAuthorize a nivel de clase
│   ├── service/UsuarioService.java       ← altas, unicidad, último admin
│   └── model/ResponsableUnh.java         ← esActivo()
├── evidencia/
│   ├── controller/EvidenciaUnhController.java  ← permisos de todo el módulo
│   └── service/EvidenciaUnhService.java        ← validación y almacenamiento
├── modulo/ModuloUnh.java                 ← "MOD_" + codigo
├── comun/excepcion/GlobalExceptionHandler.java ← respuestas de error
└── ...

backend/src/main/resources/
├── application.properties         ← ddl-auto, config.import, CORS-free
└── db/migration/
    ├── V1__esquema_inicial.sql    ← tablas + CHECK + admin
    └── V2__sufijo_unh_y_estado_activo.sql

backend/src/test/java/com/unh/mantenimiento/
└── comun/excepcion/SeguridadPorModuloTest.java  ← 7 tests de permisos

frontend/src/
├── api/cliente.js                 ← token, header Authorization, manejo de 401
└── contexto/Sesion.jsx            ← login/logout, estado en localStorage
```

---

*Documento generado el 2026-11-06 contra el commit `4d31198` + trabajo de
I5/I6 sin commitear. Cuando el código cambie, actualiza los números de línea.*
