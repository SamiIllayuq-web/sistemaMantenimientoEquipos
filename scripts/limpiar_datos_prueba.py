"""
Limpia los datos de prueba que las verificaciones dejaron en la base de
desarrollo.

Borra, en orden (por las claves foraneas):
  1. Evidencias de los mantenimientos de prueba.
  2. Mantenimientos de prueba.
  3. Incidencias de prueba.
  4. Activos de prueba.
  5. Tipos de activo y ubicaciones de prueba sin uso.

NO toca los datos reales: el activo "Terma del Comedor", el usuario admin,
el usuario angel y el usuario luis se conservan.

Uso: python3 scripts/limpiar_datos_prueba.py
"""

import subprocess
from pathlib import Path

BD = "mantenimiento"
HOST = "localhost"


def credenciales():
    """Lee las credenciales del .env de la raiz del repo."""
    env = {}
    archivo = Path(__file__).resolve().parent.parent / ".env"
    if archivo.exists():
        for linea in archivo.read_text().splitlines():
            if "=" in linea and not linea.startswith("#"):
                clave, valor = linea.split("=", 1)
                env[clave.strip()] = valor.strip()
    return {
        "DB_USER": env.get("DB_USER", "unh_mantenimiento"),
        "DB_PASSWORD": env.get("DB_PASSWORD", "unh_mantenimiento"),
        "PATH": "/usr/bin:/bin",
    }

# Datos que NUNCA se tocan: son los reales del proyecto.
PATRONES_ACTIVO_A_BORRAR = [
    "I5-PRUEBA-%",
    "I5-PREV-%",
    "I6-PRUEBA-%",
    "DBG-%",
    "PAG-%",
]

CONSULTAS = [
    # Evidencias de los mantenimientos de prueba.
    """
    DELETE FROM evidencia_mantenimiento_unh
    WHERE id_mantenimiento IN (
        SELECT m.id_mantenimiento
        FROM mantenimiento_unh m
        JOIN activo_unh a ON a.id_activo = m.id_activo
        WHERE a.codigo_patrimonial LIKE ANY (ARRAY[{patrones}])
    )
    """,
    # Mantenimientos de prueba.
    """
    DELETE FROM mantenimiento_unh
    WHERE id_activo IN (
        SELECT id_activo FROM activo_unh
        WHERE codigo_patrimonial LIKE ANY (ARRAY[{patrones}])
    )
    """,
    # Incidencias de prueba.
    """
    DELETE FROM incidencia_unh
    WHERE id_activo IN (
        SELECT id_activo FROM activo_unh
        WHERE codigo_patrimonial LIKE ANY (ARRAY[{patrones}])
    )
    """,
    # Activos de prueba.
    """
    DELETE FROM activo_unh
    WHERE codigo_patrimonial LIKE ANY (ARRAY[{patrones}])
    """,
    # Tipos de activo de prueba que ya no usa ningun activo.
    """
    DELETE FROM tipo_activo_unh
    WHERE nombre LIKE 'Tipo de prueba%'
       OR nombre LIKE 'Tipo evidencia %'
       OR nombre LIKE 'Tipo integracion %'
       OR nombre LIKE 'Tipo seguridad %'
    """,
    # Ubicaciones de prueba que ya no usa ningun activo.
    """
    DELETE FROM ubicacion_unh
    WHERE nombre LIKE 'Sala de prueba%'
       OR nombre LIKE 'Sala evidencia %'
       OR nombre LIKE 'Sala integracion %'
       OR nombre LIKE 'Sala seguridad %'
    """,
]


def ejecutar(sql):
    env = credenciales()
    proceso = subprocess.run(
        ["psql", "-h", HOST, "-U", env["DB_USER"], "-d", BD, "-v", "ON_ERROR_STOP=1", "-c", sql],
        capture_output=True,
        text=True,
        env={"PGPASSWORD": env["DB_PASSWORD"], "PATH": "/usr/bin:/bin"},
    )
    return proceso.returncode, (proceso.stdout + proceso.stderr).strip()


def main():
    patrones = ", ".join(f"'{p}'" for p in PATRONES_ACTIVO_A_BORRAR)

    for consulta in CONSULTAS:
        sql = consulta.format(patrones=patrones)
        codigo, salida = ejecutar(sql)
        primera_linea = salida.splitlines()[0] if salida else ""
        if codigo == 0:
            print(f"  OK   {primera_linea}")
        else:
            print(f"  FALLA {salida}")

    print("\n-- Como queda --")
    codigo, salida = ejecutar(
        "SELECT codigo_patrimonial, nombre, estado FROM activo_unh ORDER BY id_activo;"
    )
    print(salida)


if __name__ == "__main__":
    main()
