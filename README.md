# Sistema de Mantenimiento — Residencia Universitaria UNH

Aplicacion web para registrar los activos de la residencia universitaria de la
Universidad Nacional de Huancavelica, reportar sus fallas (incidencias) y
controlar los mantenimientos correctivos, con sus evidencias (fotos e informes).

## Documentos del proyecto

| Archivo | Que es |
|---|---|
| `coreDocs/proyecto_mantenimiento.txt` | **El QUE**: alcance, stack, esquema de BD, reglas de negocio, modulos. |
| `coreDocs/matt.txt` | **El COMO**: metodologia de trabajo (Matt Pocock). Fuente de verdad del proceso. |

> Si el doc y la metodologia chocan, manda la metodologia y se actualiza el doc.

## Estructura

```
.
├── backend/          Spring Boot 4.1.2 (Java 21) - puerto 8081
│   └── src/main/java/com/unh/mantenimiento/
│       ├── seguridad/  usuario/  ubicacion/  tipoactivo/  activo/
│       ├── incidencia/  mantenimiento/  evidencia/  comun/
├── frontend/         React 19 + Vite 8 - puerto 5174 (proxy /api -> 8081)
├── coreDocs/         Documentacion del proyecto
├── docs/             schema.json (guia de columnas, se regenera por migracion)
└── .env              Credenciales (NO se sube al repo)
```

## Requisitos

- Java 21
- Node.js 20+ (probado con 26.12)
- PostgreSQL 18 (o superior)
- Maven 3.9+ (o usar `./mvnw`)

## Puesta en marcha

### 1. Base de datos

```bash
sudo -u postgres psql -c "CREATE DATABASE mantenimiento;"
sudo -u postgres psql -c "CREATE DATABASE mantenimiento_test;"
sudo -u postgres psql -c "CREATE ROLE unh_mantenimiento LOGIN PASSWORD 'unh_mantenimiento';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE mantenimiento TO unh_mantenimiento;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE mantenimiento_test TO unh_mantenimiento;"
```

### 2. Variables de entorno

```bash
cp .env.example .env
# Editar .env: JWT_SECRET (openssl rand -base64 48) y EVIDENCIAS_DIR
```

### 3. Backend

```bash
cd backend
./mvnw spring-boot:run          # http://localhost:8081
```

El `.env` de la raiz del repo se carga solo (`spring.config.import`), asi que
NO hace falta `set -a; source ../.env`.

Las migraciones de Flyway se aplican solas al arrancar (`ddl-auto=validate`:
la app NUNCA crea ni modifica tablas).

### 3b. Usar Supabase en vez de la BD local

```bash
bash scripts/configurar_supabase.sh        # pide project ref, host, puerto y clave
bash scripts/configurar_supabase.sh --local # vuelve a la BD local
```

Usa el **pooler de sesion (puerto 5432)**: el de transaccion (6543) no
soporta los prepared statements que usa Hibernate.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev                     # http://localhost:5174
```

Vite hace proxy de `/api` hacia `http://localhost:8081`, asi que no hay CORS
en desarrollo.

### 5. Usuario inicial

```
correo:   admin@unh.edu.pe
clave:    Admin1234
```

> Cambiar esta clave en el primer ingreso.

## Pruebas

```bash
cd backend
set -a; source ../.env; set +a
./mvnw test
```

Las pruebas corren contra la BD `mantenimiento_test` (PostgreSQL real, no H2).
**TDD es obligatorio**: test rojo -> implementacion -> refactor.

## Metodologia de trabajo

Ciclo de 5 fases (ver `coreDocs/matt.txt`):

1. **Grilling** (HITL) — preguntas de una en una con respuesta recomendada.
2. **PRD** (HITL) — documento de destino: que + definicion de done.
3. **Kanban** (HITL) — issues en `.md` locales con dependencias (DAG).
4. **Implementar** (AFK) — vertical slices, TDD, commits atomicos.
5. **QA + revision** (HITL) — el humano impone el criterio; el QA genera nuevas issues.

Reglas que no se negocian: vertical slices, TDD, deep modules (interfaz chica,
logica densa), contexto limpio (nunca compactar), doc rot (borrar el PRD
implementado), cambios de BD solo por Flyway.
