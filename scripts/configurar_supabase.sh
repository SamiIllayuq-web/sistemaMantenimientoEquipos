#!/usr/bin/env bash
# =====================================================================
# Configura el .env para apuntar a Supabase (pooler de SESION, 5432).
#
# Por que el pooler de sesion y no el de transaccion (6543):
# Hibernate usa prepared statements del lado del servidor; el pooler de
# transaccion no los soporta y la app falla con
# "prepared statement ... does not exist".
#
# La contrasena se pide OCULTA (-s): no queda en el historial del shell
# ni se imprime en pantalla.
#
# Uso:  bash scripts/configurar_supabase.sh
# Revertir:  bash scripts/configurar_supabase.sh --local
# =====================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV="$RAIZ/.env"

if [ ! -f "$ENV" ]; then
    echo "ERROR: no existe $ENV"
    echo "Copia .env.example -> .env y configura JWT_SECRET primero."
    exit 1
fi

# --- Volver a la BD local (PostgreSQL de la maquina) -------------------
if [ "${1:-}" = "--local" ]; then
    cp "$ENV" "$ENV.bak.$(date +%Y%m%d%H%M%S)"
    python3 - "$ENV" <<'PY'
import re, sys
ruta = sys.argv[1]
lineas = open(ruta).read().splitlines()
salida = []
for linea in lineas:
    if linea.startswith('DB_URL=') or linea.startswith('DB_USER=') \
       or linea.startswith('DB_PASSWORD=') or linea.startswith('DB_SCHEMA='):
        continue
    salida.append(linea)
salida += [
    '',
    '# --- BD local (PostgreSQL de la maquina) ---',
    'DB_HOST=localhost',
    'DB_PORT=5432',
    'DB_NAME=mantenimiento',
    'DB_USER=unh_mantenimiento',
    'DB_PASSWORD=unh_mantenimiento',
]
open(ruta, 'w').write('\n'.join(salida) + '\n')
PY
    echo "OK: .env vuelve a la BD local (localhost:5432/mantenimiento)."
    exit 0
fi

# --- Configurar Supabase ----------------------------------------------
echo "Project ref de Supabase (ej. nvfbppvseevfiagqmtgb):"
read -r REF
if [ -z "$REF" ]; then
    echo "ERROR: el project ref no puede estar vacio."
    exit 1
fi

echo "Host del pooler [aws-1-us-west-1.pooler.supabase.com]:"
read -r HOST
HOST="${HOST:-aws-1-us-west-1.pooler.supabase.com}"

echo "Puerto del pooler [5432 = sesion, el recomendado para Hibernate]:"
read -r PUERTO
PUERTO="${PUERTO:-5432}"

echo "Contrasena de la base de datos (se escribe oculta):"
read -r -s PASS
echo
if [ -z "$PASS" ]; then
    echo "ERROR: la contrasena no puede estar vacia."
    exit 1
fi

cp "$ENV" "$ENV.bak.$(date +%Y%m%d%H%M%S)"

python3 - "$ENV" "$REF" "$HOST" "$PUERTO" "$PASS" <<'PY'
import sys
ruta, ref, host, puerto, passwd = sys.argv[1:6]

# Spring lee el .env como 'properties': un '\' o un '\uXXXX' se interpretan
# como escape. Se avisa en vez de romper en silencio.
if '\\' in passwd:
    print("AVISO: la contrasena contiene '\\'. Spring lo interpreta como escape.")
    print("       Si la conexion falla, cambiala por una sin barra invertida.")

lineas = open(ruta).read().splitlines()
salida = []
for linea in lineas:
    clave = linea.split('=', 1)[0].strip() if '=' in linea else ''
    if clave in ('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER',
                 'DB_PASSWORD', 'DB_URL', 'DB_SCHEMA'):
        continue
    salida.append(linea)

salida += [
    '',
    '# --- Supabase (pooler de sesion) ---',
    f'DB_URL=jdbc:postgresql://{host}:{puerto}/postgres',
    f'DB_USER=postgres.{ref}',
    f'DB_PASSWORD={passwd}',
    'DB_SCHEMA=public',
]
open(ruta, 'w').write('\n'.join(salida) + '\n')
PY

echo
echo "OK: .env actualizado para Supabase (backup en .env.bak.*)."
echo "Siguiente paso:  cd backend && ./mvnw spring-boot:run"
echo "Flyway creara las tablas en el esquema 'public' de Supabase."
