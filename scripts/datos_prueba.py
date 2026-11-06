#!/usr/bin/env python3
"""
Carga datos de prueba en el sistema de mantenimiento (schema 'mantenimiento').

Los datos son COHERENTES entre si: cada activo tiene tipo y ubicacion, cada
incidencia pertenece a un activo real, cada correctivo tiene su incidencia
y cada preventivo NO la tiene (lo exige el CHECK de la BD).

Uso:
    python3 scripts/datos_prueba.py            # carga (no duplica)
    python3 scripts/datos_prueba.py --limpiar  # borra lo que creo y vuelve a cargar

Las credenciales salen del .env de la raiz del repo (DB_URL, DB_USER,
DB_PASSWORD, DB_SCHEMA).
"""

import argparse
import os
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent


def leer_env() -> dict:
    """Lee el .env de la raiz (formato properties)."""
    valores = {}
    env_file = RAIZ / ".env"
    if not env_file.exists():
        sys.exit(f"ERROR: no existe {env_file}")
    for linea in env_file.read_text().splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, _, valor = linea.partition("=")
        valores[clave.strip()] = valor.strip()
    return valores


def url_jdbc_a_pg(config: dict) -> str:
    """Convierte la URL de Spring (jdbc:postgresql://...) en URL de libpq."""
    url = config.get("DB_URL", "")
    if not url:
        # Modo local: DB_HOST/DB_PORT/DB_NAME
        host = config.get("DB_HOST", "localhost")
        puerto = config.get("DB_PORT", "5432")
        base = config.get("DB_NAME", "mantenimiento")
        return f"postgresql://{config['DB_USER']}:{config['DB_PASSWORD']}@{host}:{puerto}/{base}"

    m = re.match(r"jdbc:postgresql://([^:/]+):(\d+)/([^?]+)", url)
    if not m:
        sys.exit(f"ERROR: no entiendo la DB_URL: {url}")
    host, puerto, base = m.groups()
    return (f"postgresql://{config['DB_USER']}:{config['DB_PASSWORD']}"
            f"@{host}:{puerto}/{base}")


def ejecutar(psql_bin: str, url: str, sql: str, titulo: str):
    """Ejecuta SQL con psql y devuelve el stdout."""
    import subprocess
    proceso = subprocess.run(
        [psql_bin, url, "-v", "ON_ERROR_STOP=1", "-q", "-c", sql],
        capture_output=True, text=True,
    )
    if proceso.returncode != 0:
        print(f"ERROR en [{titulo}]:\n{proceso.stderr}", file=sys.stderr)
        sys.exit(1)
    return proceso.stdout


def reiniciar_secuencias(psql_bin: str, url: str, esquema: str):
    """Deja los contadores de ID en 1 para que el siguiente INSERT use el ID 1.

    Imprescindible: si ya habia filas, las secuencias quedan desfasadas y los
    INSERTS arrancan en N+1. Como los FK de activos, incidencias y
    mantenimientos se calculan por indice, todo quedaria desplazado una
    posicion y cada activo apuntaria al tipo y la ubicacion equivocados.

    El nombre de la columna PK es distinto en cada tabla (id_activo,
    id_tipo_activo, id_ubicacion...), asi que se busca en la propia tabla.
    """
    tablas = ["ubicacion_unh", "tipo_activo_unh", "activo_unh",
              "incidencia_unh", "mantenimiento_unh", "evidencia_mantenimiento_unh"]
    for tabla in tablas:
        # La secuencia se llama <tabla>_id_<columna>_seq: se deriva del
        # nombre real de la columna PRIMARY KEY de esa tabla.
        sql = f"""
        DO $$
        DECLARE
            col text;
            seq text;
        BEGIN
            SELECT a.attname INTO col
            FROM pg_index i
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
            WHERE i.indrelid = '{esquema}.{tabla}'::regclass AND i.indisprimary
            LIMIT 1;

            seq := pg_get_serial_sequence('{esquema}.{tabla}', col);
            IF seq IS NOT NULL THEN
                EXECUTE format('SELECT setval(%L, 1, false)', seq);
            END IF;
        END $$;
        """
        ejecutar(psql_bin, url, sql, f"secuencia {tabla}")


def borrar_todo(psql_bin: str, url: str, esquema: str):
    """Borra TODOS los datos de las tablas del sistema (deja el esquema vacio).

    Se usa antes de cargar los datos de prueba para que los IDs empieces en 1
    y las referencias entre tablas sean exactas.
    """
    sql = f"""
    DELETE FROM {esquema}.evidencia_mantenimiento_unh;
    DELETE FROM {esquema}.mantenimiento_unh;
    DELETE FROM {esquema}.incidencia_unh;
    DELETE FROM {esquema}.activo_unh;
    DELETE FROM {esquema}.tipo_activo_unh;
    DELETE FROM {esquema}.ubicacion_unh;
    """
    ejecutar(psql_bin, url, sql, "borrar")


# --------------------------------------------------------------------------
# Los datos
# --------------------------------------------------------------------------

UBICACIONES = [
    ("Sala de Computo 1", "SALA", "Pabellon A", "1", "Laboratorio de computo con 20 equipos"),
    ("Sala de Computo 2", "SALA", "Pabellon A", "1", "Laboratorio de redes y programacion"),
    ("Laboratorio de Electronica", "LABORATORIO", "Pabellon B", "2", "Instrumentos de medicion y soldadura"),
    ("Aula de Seguridad", "AULA", "Pabellon A", "2", "Aula teorica de seguridad de la residencia"),
    ("Biblioteca Central", "BIBLIOTECA", "Pabellon C", "1", "Equipos de catalogacion y OPDS"),
    ("Oficina de Administracion", "OFICINA", "Pabellon A", "3", "Administracion de la residencia"),
    ("Comedor Universitario", "COMEDOR", "Pabellon D", "1", "Punto de venta y cocina"),
    ("Sala de Proyectos", "SALA", "Pabellon B", "3", "Trabajo de tesis y prototipos"),
    ("Deposito de Equipamiento", "DEPOSITO", "Pabellon D", "2", "Almacen de equipos de respaldo"),
    ("Laboratorio de Computo", "LABORATORIO", "Pabellon C", "2", "Servidores y equipos de alto rendimiento"),
]

TIPOS_ACTIVO = [
    ("Computadora de escritorio", "EQUIPO", "Equipo de computo para areas de trabajo", True, 5),
    ("Laptop", "EQUIPO", "Computadora portatil para trabajo movil", True, 4),
    ("Proyector", "EQUIPO", "Proyector para salones de clase", True, 6),
    ("Impresora", "EQUIPO", "Impresora y escaner del area administrativa", True, 7),
    ("Servidor", "EQUIPO", "Servidor de aplicacion y base de datos", True, 8),
    ("Router de red", "EQUIPO", "Equipos de conectividad del campus", True, 8),
    ("Monitor", "EQUIPO", "Monitor de escritorio", True, 6),
    ("Sistema de alarma", "INFRAESTRUCTURA", "Central de alarma y detectors", True, 10),
    ("Bomba de agua", "INFRAESTRUCTURA", "Bomba del tanque de reserva", True, 12),
    ("Aire acondicionado", "INFRAESTRUCTURA", "Equipo de climatizacion de aulas", True, 10),
]

ACTIVOS = [
    # codigo, nombre, marca, modelo, serie, f_adq, f_serv, estado_op, valor, tipo, ubicacion, obs
    ("UNH-PC-001", "Computadora de escritorio - Sala 1", "Lenovo", "ThinkCentre M70", "SN8K2201", "2023-03-15", "2023-04-02", "OPERATIVO", 3200.00, 0, 0, "Equipo principal del docente"),
    ("UNH-PC-002", "Computadora de escritorio - Sala 2", "Lenovo", "ThinkCentre M70", "SN8K2202", "2023-03-15", "2023-04-02", "OPERATIVO", 3200.00, 0, 0, ""),
    ("UNH-PC-003", "Laptop del laboratorio", "Dell", "Latitude 5440", "DL544001", "2023-08-20", "2023-09-10", "OPERATIVO", 4100.00, 1, 2, "Para uso de estudiantes"),
    ("UNH-PC-004", "Laptop del aula de seguridad", "HP", "ProBook 450", "HP450003", "2024-01-15", "2024-02-01", "OPERATIVO", 3800.00, 1, 3, ""),
    ("UNH-PC-005", "Monitor de la sala 1", "Dell", "P2422H", "DL242201", "2023-03-15", "2023-04-02", "OPERATIVO", 480.00, 6, 0, "Monitor del docente"),
    ("UNH-PC-006", "Proyector del aula 1", "Epson", "EB-X05", "EPX0501", "2022-11-10", "2022-11-25", "OPERATIVO", 2100.00, 2, 3, "Proyector del aula de seguridad"),
    ("UNH-PC-007", "Proyector del aula 2", "Epson", "EB-X05", "EPX0502", "2022-11-10", "2022-11-25", "OPERATIVO", 2100.00, 2, 3, ""),
    ("UNH-PC-008", "Impresora de administracion", "HP", "LaserJet M404", "HPM40401", "2021-06-05", "2021-06-20", "OPERATIVO", 950.00, 3, 5, ""),
    ("UNH-PC-009", "Impresora de la biblioteca", "Canon", "LBP2900", "CN290001", "2020-09-01", "2020-09-15", "FUERA_DE_SERVICIO", 720.00, 3, 4, "Impresora danada, pendiente de baja"),
    ("UNH-PC-010", "Servidor de aplicaciones", "Dell", "PowerEdge R450", "DLPE4501", "2024-02-10", "2024-03-01", "OPERATIVO", 12500.00, 4, 8, "Servidor del laboratorio de computo"),
    ("UNH-PC-011", "Servidor de base de datos", "Dell", "PowerEdge R450", "DLPE4502", "2024-02-10", "2024-03-01", "OPERATIVO", 12500.00, 4, 8, ""),
    ("UNH-PC-012", "Router principal del campus", "Cisco", "ISR4331", "CS433101", "2023-05-20", "2023-06-10", "OPERATIVO", 5600.00, 5, 8, "Nodo central de la red"),
    ("UNH-PC-013", "Router del laboratorio", "Cisco", "ISR1100", "CS110001", "2022-04-11", "2022-05-02", "OPERATIVO", 1800.00, 5, 2, ""),
    ("UNH-PC-014", "Switch de acceso", "TP-Link", "TL-SG1024", "TPSG10241", "2021-02-18", "2021-03-01", "OPERATIVO", 420.00, 5, 8, ""),
    ("UNH-PC-015", "Monitor de la sala 2", "Dell", "P2422H", "DL242202", "2023-03-15", "2023-04-02", "OPERATIVO", 480.00, 6, 1, ""),
    ("UNH-PC-016", "Monitor de la oficina", "Samsung", "S24R350", "SMS24R351", "2023-10-05", "2023-10-20", "OPERATIVO", 390.00, 6, 5, ""),
    ("UNH-PC-017", "Alarma del deposito", "Bosch", "DS160", "BS160001", "2022-08-22", "2022-09-05", "OPERATIVO", 890.00, 7, 8, "Central de alarma"),
    ("UNH-PC-018", "Alarma de la biblioteca", "Bosch", "DS160", "BS160002", "2022-08-22", "2022-09-05", "OPERATIVO", 890.00, 7, 4, ""),
    ("UNH-PC-019", "Bomba del tanque de reserva", "Grundfos", "Hydro MPC-E 2", "GRMPC2001", "2020-01-30", "2020-02-20", "EN_MANTENIMIENTO", 6500.00, 8, 8, "En mantenimiento por falla en el sello"),
    ("UNH-PC-020", "Aire acondicionado del comedor", "Carrier", "Xsplit 36K", "CAR36K001", "2023-07-12", "2023-08-01", "OPERATIVO", 5200.00, 9, 6, ""),
    ("UNH-PC-021", "Aire acondicionado del aula 1", "Carrier", "Xsplit 36K", "CAR36K002", "2023-07-12", "2023-08-01", "OPERATIVO", 5200.00, 9, 3, ""),
    ("UNH-PC-022", "Aire acondicionado de la sala 1", "Midea", "MSAP-12", "MD12K001", "2021-12-03", "2022-01-15", "FUERA_DE_SERVICIO", 4300.00, 9, 0, "No enciende, en espera de repuesto"),
    ("UNH-PC-023", "Laptop de la direccion", "MacBook", "MacBook Pro 14", "MBP14001", "2024-05-20", "2024-06-01", "OPERATIVO", 8900.00, 1, 5, ""),
    ("UNH-PC-024", "Laptop del responsable", "MacBook", "MacBook Air M2", "MBA20001", "2024-05-20", "2024-06-01", "OPERATIVO", 6200.00, 1, 7, ""),
    ("UNH-PC-025", "Monitor de la sala de proyectos", "Acer", "Nitro V5", "ACNV50001", "2024-09-10", "2024-09-25", "OPERATIVO", 750.00, 6, 7, ""),
    ("UNH-PC-026", "Servidor de respaldo", "HPE", "ProLiant DL380", "HPDL38001", "2023-11-20", "2023-12-10", "OPERATIVO", 9800.00, 4, 8, ""),
    ("UNH-PC-027", "Proyector de la biblioteca", "BenQ", "E520", "BQE52001", "2022-03-15", "2022-04-01", "OPERATIVO", 2400.00, 2, 4, ""),
    ("UNH-PC-028", "Router del comedor", "MikroTik", "RB4011", "MK401101", "2024-02-25", "2024-03-15", "OPERATIVO", 890.00, 5, 6, ""),
    ("UNH-PC-029", "Impresora de etiquetado", "Zebra", "ZD420", "ZBD42001", "2022-10-08", "2022-11-01", "OPERATIVO", 1450.00, 3, 5, "Para el deposito de equipamiento"),
    ("UNH-PC-030", "Laptop del laboratorio de electronica", "Acer", "TravelMate P2", "ACTMP2001", "2023-06-22", "2023-07-10", "OPERATIVO", 3400.00, 1, 2, ""),
]

INCIDENCIAS = [
    # descripcion, prioridad, estado, activo_idx, fecha_deteccion, fecha_cierre, obs
    ("El equipo no enciende al presionar el boton de encendido", "ALTA", "CERRADA", 18,
     "2026-09-02 09:15:00", "2026-09-06 11:00:00", "Se cambio la fuente de poder"),
    ("La pantalla muestra rayas verticales y parpadea", "MEDIA", "CERRADA", 4,
     "2026-09-08 14:30:00", "2026-09-10 10:45:00", "Cambio de panel de display"),
    ("El proyector no recibe senal del equipo", "MEDIA", "CERRADA", 6,
     "2026-09-12 08:20:00", "2026-09-15 16:00:00", "Cable HDMI defectuoso"),
    ("La impresora emite un error de toner en la pantalla", "BAJA", "CERRADA", 8,
     "2026-09-18 10:00:00", "2026-09-18 15:30:00", "Se recambio el toner"),
    ("Fuga de agua en la base de la bomba", "ALTA", "CERRADA", 18,
     "2026-09-25 07:50:00", "2026-10-02 13:20:00", "Sello mecanico cambiado"),
    ("El router pierde la conexion cada cierto tiempo", "ALTA", "CERRADA", 11,
     "2026-10-05 11:40:00", "2026-10-08 09:50:00", "Actualizacion de firmware"),
    ("El aire acondicionado no enfria el aula", "MEDIA", "EN_ATENCION", 20,
     "2026-10-12 13:10:00", None, "Tecnico revisara el refrigerante"),
    ("El aire acondicionado no enciende en absoluto", "ALTA", "EN_ATENCION", 21,
     "2026-10-15 09:05:00", None, "En espera de la placa de control"),
    ("La impresora de la biblioteca no responde", "MEDIA", "EN_ATENCION", 8,
     "2026-10-18 15:20:00", None, "Se verificara el cable de red"),
    ("El equipo del aula presenta lentitud al abrir archivos", "BAJA", "ABIERTA", 2,
     "2026-11-02 10:30:00", None, "Pendiente de evaluar la unidad de disco"),
    ("La laptop del laboratorio no carga la bateria", "MEDIA", "ABIERTA", 29,
     "2026-11-04 14:10:00", None, "Posible bateria danada"),
    ("El servidor de respaldo emite alarma de energia", "ALTA", "ABIERTA", 25,
     "2026-11-05 08:45:00", None, "Revisar la UPS del deposito"),
]

MANTENIMIENTOS = [
    # tipo, id_activo, id_incidencia, fecha_inicio, fecha_fin, diag, actividad, resultado, estado, horas, costo, prox, obs
    ("CORRECTIVO", 18, 0, "2026-09-02 10:00:00", "2026-09-06 11:00:00",
     "Fuente de poder defectuosa", "Se reemplazo la fuente de poder",
     "Equipo operativo", "FINALIZADO", 6.0, 180.00, None, ""),
    ("CORRECTIVO", 4, 1, "2026-09-08 15:00:00", "2026-09-10 10:45:00",
     "Panel de display con pixeles muertos", "Cambio de panel de display",
     "Imagen correcta", "FINALIZADO", 5.0, 420.00, None, ""),
    ("CORRECTIVO", 6, 2, "2026-09-12 09:00:00", "2026-09-15 16:00:00",
     "Cable HDMI en mal estado", "Se sustituyo el cable HDMI",
     "Senal estable", "FINALIZADO", 4.0, 60.00, None, ""),
    ("CORRECTIVO", 8, 3, "2026-09-18 11:00:00", "2026-09-18 15:30:00",
     "Toner agotado", "Recambio de toner", "Impresion normal", "FINALIZADO", 4.0, 95.00, None, ""),
    ("CORRECTIVO", 18, 4, "2026-09-25 09:00:00", "2026-10-02 13:20:00",
     "Sello mecanico de la bomba deteriorado", "Cambio de sello y alineacion de impulsor",
     "Bomba sin fugas", "FINALIZADO", 24.0, 780.00, "2027-04-02", "Mantenimiento semestral recomendado"),
    ("CORRECTIVO", 11, 5, "2026-10-05 12:00:00", "2026-10-08 09:50:00",
     "Firmware con version inestable", "Actualizacion de firmware a la version estable",
     "Conexion estable", "FINALIZADO", 8.0, 0.00, None, ""),
    ("CORRECTIVO", 20, 6, "2026-10-12 14:00:00", None,
     "Carga de refrigerante insuficiente", "En proceso de revision del refrigerante",
     None, "EN_PROCESO", 3.0, 150.00, None, "Tecnico asignado"),
    ("CORRECTIVO", 21, 7, "2026-10-15 10:00:00", None,
     "Posible falla de la placa de control", "En espera del repuesto",
     None, "EN_PROCESO", 2.0, 0.00, None, "Falta la placa de control"),
    ("CORRECTIVO", 8, 8, "2026-10-18 16:00:00", None,
     "Cable de red danado", "En proceso de verificacion del cable",
     None, "EN_PROCESO", 1.0, 0.00, None, ""),
    ("PREVENTIVO", 0, None, "2026-11-01 09:00:00", "2026-11-01 12:00:00",
     "Limpieza de componentes internos y cambio de pasta termica",
     "Se limpio el equipo y se cambio la pasta termica",
     "Temperatura estable", "FINALIZADO", 3.0, 80.00, "2027-05-01", ""),
    ("PREVENTIVO", 9, None, "2026-11-01 14:00:00", "2026-11-01 16:00:00",
     "Revision periodica del sistema", "Limpieza y verificacion de temperatura",
     "Sin novedades", "FINALIZADO", 2.0, 45.00, "2027-05-01", ""),
    ("PREVENTIVO", 3, None, "2026-11-02 10:00:00", None,
     "Revision periodica del sistema", "En proceso de verificacion",
     None, "EN_PROCESO", 1.0, 30.00, None, ""),
    ("PREVENTIVO", 11, None, "2026-11-03 11:00:00", None,
     "Verificacion de la configuracion de red", "En proceso de configuracion",
     None, "EN_PROCESO", 1.0, 0.00, None, ""),
    ("PREVENTIVO", 19, None, "2026-11-04 10:00:00", "2026-11-04 13:00:00",
     "Revision de la instalacion electrica", "Se reviso el cableado y los tomacorrientes",
     "Instalacion segura", "FINALIZADO", 3.0, 65.00, "2027-05-04", ""),
    ("PREVENTIVO", 26, None, "2026-11-05 09:00:00", None,
     "Limpieza de ventiladores y cambio de filtros", "En proceso de limpieza",
     None, "EN_PROCESO", 1.0, 25.00, None, ""),
]

EVIDENCIAS = [
    # id_mant, tipo, nombre_archivo, nombre_original, descripcion
    (0, "FOTO", "a1b2c3d4-demo-foto1.png", "foto-equipo-1.png", "Estado inicial del equipo"),
    (0, "INFORME", "e5f6g7h8-demo-informe1.pdf", "informe-tecnico.pdf", "Informe tecnico de la fuente de poder"),
    (1, "FOTO", "i9j0k1l2-demo-foto2.png", "monitor-rayas.png", "Detalle de las rayas en pantalla"),
    (4, "FOTO", "m3n4o5p6-demo-foto3.png", "fuga-bomba.jpg", "Fuga de agua en la base de la bomba"),
    (4, "INFORME", "q7r8s9t0-demo-informe2.pdf", "informe-sello.pdf", "Informe del cambio de sello"),
    (6, "FOTO", "u1v2w3x4-demo-foto4.png", "aire-acondicionado.jpg", "Aire acondicionado sin enfriar"),
    (9, "INFORME", "y5z6a7b8-demo-informe3.pdf", "preventivo-limpieza.pdf", "Acta de limpieza preventiva"),
    (13, "FOTO", "c9d0e1f2-demo-foto5.png", "cableado.jpg", "Estado del cableado revisado"),
]


def sql_lista(valores, escapado=True):
    """Convierte una lista de tuplas en VALUES de SQL."""
    partes = []
    for fila in valores:
        celdas = []
        for v in fila:
            if v is None:
                celdas.append("NULL")
            elif isinstance(v, (int, float)):
                celdas.append(str(v))
            else:
                escapado = str(v).replace("'", "''")
                celdas.append(f"'{escapado}'")
        partes.append("(" + ", ".join(celdas) + ")")
    return ", ".join(partes)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limpiar", action="store_true",
                        help="borra los datos de prueba anteriores")
    parser.add_argument("--solo-datos", action="store_true",
                        help="no borra nada: agrega a lo que ya hay (puede "
                             "desalinear los ID, usalo solo si sabes lo que haces)")
    args = parser.parse_args()

    config = leer_env()
    url = url_jdbc_a_pg(config)
    esquema = config.get("DB_SCHEMA", "public")
    psql = "psql"

    print(f"Conectando a: {url.split('@')[1]}")
    print(f"Esquema: {esquema}\n")

    if args.limpiar or not args.solo_datos:
        print("Limpiando las tablas del sistema...")
        borrar_todo(psql, url, esquema)
        reiniciar_secuencias(psql, url, esquema)
        print("OK\n")
    else:
        print("AVISO: se agrega a los datos existentes sin limpiar.\n")

    # --- Ubicaciones ---
    print("Insertando ubicaciones...")
    sql = (f"INSERT INTO {esquema}.ubicacion_unh "
           f"(nombre, tipo_ubicacion, pabellon, piso, descripcion) VALUES ")
    sql += sql_lista(UBICACIONES) + ";"
    ejecutar(psql, url, sql, "ubicaciones")

    # --- Tipos de activo ---
    print("Insertando tipos de activo...")
    sql = (f"INSERT INTO {esquema}.tipo_activo_unh "
           f"(nombre, categoria, descripcion, requiere_mantenimiento, vida_util_anios) VALUES ")
    sql += sql_lista(TIPOS_ACTIVO) + ";"
    ejecutar(psql, url, sql, "tipos")

    # --- Activos ---
    print("Insertando activos...")
    columnas = ("codigo_patrimonial, nombre, marca, modelo, numero_serie, "
                 "fecha_adquisicion, fecha_puesta_servicio, estado_operativo, "
                 "valor_adquisicion, id_tipo_activo, id_ubicacion, observaciones")
    filas = []
    for (codigo, nombre, marca, modelo, serie, f_adq, f_serv, estado_op,
         valor, idx_tipo, idx_ubi, obs) in ACTIVOS:
        filas.append((codigo, nombre, marca, modelo, serie, f_adq, f_serv,
                      estado_op, valor, idx_tipo + 1, idx_ubi + 1, obs))
    sql = f"INSERT INTO {esquema}.activo_unh ({columnas}) VALUES "
    sql += sql_lista(filas) + ";"
    ejecutar(psql, url, sql, "activos")

    # --- Incidencias ---
    print("Insertando incidencias...")
    filas = []
    for (desc, prio, estado, idx_activo, f_det, f_cierre, obs) in INCIDENCIAS:
        filas.append((f_det, desc, prio, estado, f_cierre, obs, idx_activo + 1))
    sql = (f"INSERT INTO {esquema}.incidencia_unh "
           f"(fecha_deteccion_incidencia, descripcion, prioridad_incidencia, "
           f"estado_incidencia, fecha_cierre_incidencia, observaciones_incidencia, "
           f"id_activo) VALUES ") + sql_lista(filas) + ";"
    ejecutar(psql, url, sql, "incidencias")

    # --- Mantenimientos ---
    print("Insertando mantenimientos...")
    filas = []
    for (tipo, idx_activo, idx_inc, f_ini, f_fin, diag, act, res, estado,
         horas, costo, prox, obs) in MANTENIMIENTOS:
        filas.append((idx_activo + 1, "00000000", idx_inc + 1 if idx_inc is not None else None,
                      tipo, f_ini, f_fin, diag, act, res, estado, horas, costo, prox, obs))
    sql = (f"INSERT INTO {esquema}.mantenimiento_unh "
           f"(id_activo, id_responsable, id_incidencia, tipo_mantenimiento, "
           f"fecha_inicio, fecha_fin, diagnostico, actividad_realizada, resultado, "
           f"estado_mantenimiento, horas_inactividad, costo_mano_obra, "
           f"proxima_fecha_mantenimiento, observaciones_mantenimiento) VALUES ")
    sql += sql_lista(filas) + ";"
    ejecutar(psql, url, sql, "mantenimientos")

    # --- Evidencias ---
    print("Insertando evidencias...")
    filas = []
    for (idx_mant, tipo, nombre_arch, nombre_orig, desc) in EVIDENCIAS:
        filas.append((idx_mant + 1, tipo, nombre_arch, nombre_orig, desc))
    sql = (f"INSERT INTO {esquema}.evidencia_mantenimiento_unh "
           f"(id_mantenimiento, tipo_archivo, nombre_archivo, nombre_original, "
           f"descripcion_evidencia) VALUES ") + sql_lista(filas) + ";"
    ejecutar(psql, url, sql, "evidencias")

    # --- Resumen ---
    print("\n" + "=" * 55)
    print("DATOS DE PRUEBA CARGADOS")
    print("=" * 55)
    for tabla in ["ubicacion_unh", "tipo_activo_unh", "activo_unh",
                  "incidencia_unh", "mantenimiento_unh", "evidencia_mantenimiento_unh"]:
        salida = ejecutar(psql, url, f"SELECT count(*) FROM {esquema}.{tabla};", "resumen")
        total = salida.strip()
        print(f"  {tabla:35} {total}")
    print("\nAbre http://localhost:5174 y entra con admin@unh.edu.pe / Admin1234")


if __name__ == "__main__":
    main()
