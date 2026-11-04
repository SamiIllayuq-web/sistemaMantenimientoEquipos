#!/usr/bin/env python3
"""Genera docs/schema.json (guia de columnas) desde la BD de desarrollo.

Uso:  python3 docs/generar_schema.py
Requiere: psycopg2 (pip install psycopg2-binary) o usar el .sql de_regsenerar.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

SQL = Path(__file__).parent / "regenerar-schema.sql"
SALIDA = Path(__file__).parent / "schema.json"

RAIZ = Path(__file__).resolve().parent.parent


def leer_env() -> dict:
    env = {}
    archivo = RAIZ / ".env"
    if archivo.exists():
        for linea in archivo.read_text().splitlines():
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                env[k.strip()] = v.strip()
    return env


def main() -> int:
    env = leer_env()
    usuario = env.get("DB_USER", "unh_mantenimiento")
    password = env.get("DB_PASSWORD", "unh_mantenimiento")
    host = env.get("DB_HOST", "localhost")
    puerto = env.get("DB_PORT", "5432")
    base = env.get("DB_NAME", "mantenimiento")

    sql = SQL.read_text()

    # Opcion 1: psycopg2
    try:
        import psycopg2

        conexion = psycopg2.connect(
            host=host, port=puerto, dbname=base,
            user=usuario, password=password,
        )
        with conexion.cursor() as cur:
            cur.execute(sql)
            fila = cur.fetchone()
            datos = fila[0] if fila else None
        conexion.close()
    except ImportError:
        # Opcion 2: psql via subprocess
        cmd = [
            "psql", "-U", usuario, "-h", host, "-p", puerto, "-d", base,
            "-At", "-f", str(SQL),
        ]
        resultado = subprocess.run(
            cmd, capture_output=True, text=True,
            env={**os.environ, "PGPASSWORD": password},
        )
        if resultado.returncode != 0:
            print("ERROR ejecutando psql:", resultado.stderr, file=sys.stderr)
            return 1
        datos = resultado.stdout.strip()

    if not datos:
        print("ERROR: la consulta no devolvio datos", file=sys.stderr)
        return 1

    # Validar que es JSON correcto antes de escribir
    esquema = json.loads(datos)

    SALIDA.write_text(json.dumps(esquema, indent=2, ensure_ascii=False) + "\n")

    print(f"OK -> {SALIDA}")
    print(f"   {len(esquema)} tablas:")
    for tabla, definicion in esquema.items():
        print(f"     - {tabla}: {len(definicion['columns'])} columnas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
