"""
Verificacion del incremento I5 (Mantenimiento correctivo) por API real.

Habla HTTP con urllib y devuelve codigos de estado reales: las anotaciones
en el codigo no prueban que el servidor valide los permisos.

Uso: python3 scripts/verificar_i5.py
"""

import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://localhost:8081"

ADMIN = ("admin@unh.edu.pe", "Admin1234")
# Usuario con el modulo ACTIVOS pero SIN MANTENIMIENTOS.
SIN_MODULO = ("luis@unh.edu.pe", "ClaveSegura123")


def pedir(metodo, ruta, token=None, cuerpo=None):
    """Devuelve (codigo, cuerpo parseado)."""
    peticion = urllib.request.Request(BASE + ruta, method=metodo)
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    datos = None
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode()
        peticion.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(peticion, datos) as respuesta:
            texto = respuesta.read()
            return respuesta.status, (json.loads(texto) if texto else {})
    except urllib.error.HTTPError as error:
        texto = error.read()
        try:
            return error.code, json.loads(texto)
        except ValueError:
            return error.code, texto.decode()[:300]


def objeto(respuesta):
    """La respuesta como dict. Si no lo es, se devuelve vacio."""
    return respuesta if isinstance(respuesta, dict) else {}


def lista(respuesta):
    """La respuesta como lista. Si no lo es, se devuelve vacia."""
    return respuesta if isinstance(respuesta, list) else []


def entrar(correo, clave):
    codigo, cuerpo = pedir("POST", "/api/auth/login", cuerpo={"correo": correo, "clave": clave})
    if codigo != 200:
        raise SystemExit(f"No se pudo iniciar sesion como {correo}: {codigo} {cuerpo}")
    return str(objeto(cuerpo).get("token", ""))


def titulo(texto):
    print("\n" + "=" * 68)
    print(texto)
    print("=" * 68)


def main():
    token_admin = entrar(*ADMIN)
    token_sin = entrar(*SIN_MODULO)

    titulo("1. MATRIZ DE PERMISOS (codigos reales)")
    # Sin token.
    codigo, _ = pedir("GET", "/api/mantenimientos")
    print(f"  lectura sin token                    -> {codigo}  (esperado 401)")
    # Lectura sin el modulo: debe funcionar.
    codigo, _ = pedir("GET", "/api/mantenimientos", token_sin)
    print(f"  lectura usuario SIN modulo          -> {codigo}  (esperado 200)")
    # Escritura sin el modulo: debe rebotar.
    codigo, _ = pedir("POST", "/api/mantenimientos/preventivo", token_sin,
                      {"idActivo": 1, "observaciones": "sin permiso"})
    print(f"  escritura usuario SIN modulo         -> {codigo}  (esperado 403)")
    # Escritura con el modulo: se usa un activo propio, porque los datos de
    # dev tienen activos desactivados y un preventivo sobre uno se rechaza.
    codigo, tipos = pedir("GET", "/api/tipos-activo", token_admin)
    codigo, ubicaciones = pedir("GET", "/api/ubicaciones", token_admin)
    lista_tipos = lista(tipos)
    lista_ubicaciones = lista(ubicaciones)
    if not lista_tipos or not lista_ubicaciones:
        raise SystemExit("Faltan catalogos para crear el activo de prueba")

    codigo_patrimonial = f"I5-PRUEBA-{int(time.time())}"
    codigo, activo_creado = pedir("POST", "/api/activos", token_admin, {
        "codigoPatrimonial": codigo_patrimonial,
        "nombre": "Activo de prueba I5",
        "idTipoActivo": lista_tipos[0]["id"],
        "idUbicacion": lista_ubicaciones[0]["id"],
        "estadoOperativo": "OPERATIVO",
    })
    print(f"  escritura admin CON modulo           -> {codigo}  (esperado 201)")
    if codigo != 201:
        print(f"  cuerpo del error: {activo_creado}")
    activo = objeto(activo_creado).get("id")

    titulo("2. FLUJO CORRECTIVO: incidencia -> EN_ATENCION -> CERRADA")
    if not activo:
        raise SystemExit(f"No se pudo crear el activo de prueba: {activo_creado}")
    print(f"  activo de prueba: {activo} ({codigo_patrimonial})")

    codigo, incidencia = pedir("POST", "/api/incidencias", token_admin, {
        "idActivo": activo,
        "descripcion": "Falla de prueba I5",
        "prioridad": "ALTA",
    })
    print(f"  crear incidencia                     -> {codigo}  (esperado 201)")
    id_incidencia = objeto(incidencia).get("id")

    if id_incidencia:
        codigo, inc = pedir("GET", f"/api/incidencias/{id_incidencia}", token_admin)
        print(f"  estado inicial de la incidencia      -> {objeto(inc).get('estado')}  (esperado ABIERTA)")

        # Fecha de inicio en el PASADO: deja margen para que la fecha de fin
        # (tambien pasada) sea posterior al inicio.
        fecha_inicio = (datetime.now() - timedelta(hours=3)).isoformat(timespec="seconds")
        codigo, mant = pedir("POST", "/api/mantenimientos/correctivo", token_admin, {
            "idIncidencia": id_incidencia,
            "fechaInicio": fecha_inicio,
            "observaciones": "Inicio del trabajo",
        })
        print(f"  crear correctivo                     -> {codigo}  (esperado 201)")
        id_mant = objeto(mant).get("id")

        if id_mant:
            codigo, inc = pedir("GET", f"/api/incidencias/{id_incidencia}", token_admin)
            print(f"  incidencia tras crear correctivo     -> {objeto(inc).get('estado')}  (esperado EN_ATENCION)")
            codigo, act = pedir("GET", f"/api/activos/{activo}", token_admin)
            print(f"  activo tras crear correctivo         -> {objeto(act).get('estadoOperativo')}  (esperado EN_MANTENIMIENTO)")

            # Un segundo correctivo sobre la misma incidencia debe rebotar.
            # OperacionInvalidaException -> 422 (convention del proyecto).
            codigo, _ = pedir("POST", "/api/mantenimientos/correctivo", token_admin, {
                "idIncidencia": id_incidencia,
            })
            print(f"  segundo correctivo misma incidencia  -> {codigo}  (esperado 422)")

            # Finalizar. La fecha de fin NO puede ser futura (PastOrPresent),
            # asi que se usa una hora antes del momento actual.
            fecha_fin = (datetime.now() - timedelta(hours=2)).isoformat(timespec="seconds")
            codigo, fin = pedir("PUT", f"/api/mantenimientos/{id_mant}/finalizar", token_admin, {
                "fechaFin": fecha_fin,
                "diagnostico": "Diagnostico de prueba",
                "actividadRealizada": "Se reparo",
                "resultado": "Reparado",
            })
            print(f"  finalizar                            -> {codigo}  (esperado 200)")
            print(f"  estado del mantenimiento              -> {objeto(fin).get('estadoMantenimiento')}  (esperado FINALIZADO)")

            codigo, inc = pedir("GET", f"/api/incidencias/{id_incidencia}", token_admin)
            print(f"  incidencia tras finalizar            -> {objeto(inc).get('estado')}  (esperado CERRADA)")
            print(f"  fecha de cierre de la incidencia      -> {objeto(inc).get('fechaCierre')}  (esperado con valor)")
            codigo, act = pedir("GET", f"/api/activos/{activo}", token_admin)
            print(f"  activo tras finalizar                -> {objeto(act).get('estadoOperativo')}  (esperado OPERATIVO)")

            # Finalizar dos veces debe rebotar (OperacionInvalidaException -> 422).
            codigo, _ = pedir("PUT", f"/api/mantenimientos/{id_mant}/finalizar", token_admin, {
                "fechaFin": fecha_fin,
            })
            print(f"  finalizar dos veces                  -> {codigo}  (esperado 422)")

            # Historial del activo.
            codigo, historial = pedir("GET", f"/api/mantenimientos/activo/{activo}", token_admin)
            print(f"  historial del activo                  -> {codigo}  (esperado 200, {len(lista(historial))} registro(s))")

    titulo("3. SELECTORES (sin paginar: deben traer todo)")
    codigo, opciones = pedir("GET", "/api/incidencias/opciones", token_admin)
    print(f"  /api/incidencias/opciones             -> {codigo}  ({len(lista(opciones))} abierta(s))")
    codigo, opciones_act = pedir("GET", "/api/activos/opciones", token_admin)
    print(f"  /api/activos/opciones                 -> {codigo}  ({len(lista(opciones_act))} activo(s))")

    titulo("4. PREVENTIVO Y REGLAS DE NEGOCIO")
    # El preventivo se hace sobre un activo PROPIO y OPERATIVO: un preventivo
    # NO cambia el estado del activo ni de ninguna incidencia.
    codigo, preventivo_activo = pedir("POST", "/api/activos", token_admin, {
        "codigoPatrimonial": f"I5-PREV-{int(time.time())}",
        "nombre": "Activo preventivo I5",
        "idTipoActivo": lista_tipos[0]["id"],
        "idUbicacion": lista_ubicaciones[0]["id"],
        "estadoOperativo": "OPERATIVO",
    })
    activo_preventivo = objeto(preventivo_activo).get("id")
    print(f"  crear activo para preventivo          -> {codigo}  (esperado 201)")

    codigo, preventivo = pedir("POST", "/api/mantenimientos/preventivo", token_admin, {
        "idActivo": activo_preventivo,
        "observaciones": "Limpieza de prueba",
    })
    print(f"  crear preventivo (sin incidencia)      -> {codigo}  (esperado 201)")
    print(f"  preventivo sin incidencia             -> {objeto(preventivo).get('incidencia')}  (esperado None)")

    # Un preventivo NO cambia el estado del activo.
    codigo, act = pedir("GET", f"/api/activos/{activo_preventivo}", token_admin)
    print(f"  activo tras preventivo                -> {objeto(act).get('estadoOperativo')}  (esperado OPERATIVO)")

    # Un correctivo sobre un activo INACTIVO se rechaza.
    codigo, inactivos = pedir("GET", "/api/activos?estado=INACTIVO", token_admin)
    inactivos_lista = lista(objeto(inactivos).get("contenido"))
    if inactivos_lista:
        id_inactivo = inactivos_lista[0]["id"]
        codigo, inc_inactivo = pedir("POST", "/api/incidencias", token_admin, {
            "idActivo": id_inactivo,
            "descripcion": "Prueba sobre activo inactivo",
            "prioridad": "BAJA",
        })
        print(f"  incidencia sobre activo INACTIVO      -> {codigo}  (esperado 422, se rechaza)")
        print(f"  motivo                                -> {objeto(inc_inactivo).get('mensaje')}")

    # Un correctivo sin incidencia valida se rechaza.
    codigo, cuerpo = pedir("POST", "/api/mantenimientos/correctivo", token_admin, {
        "idIncidencia": 999999,
    })
    print(f"  correctivo con incidencia inexistente -> {codigo}  (esperado 404)")

    titulo("5. PAGINACION Y FILTROS DE MANTENIMIENTOS")
    codigo, pagina = pedir("GET", "/api/mantenimientos?pagina=1&tamano=10", token_admin)
    print(f"  pagina 1 tamano 10                    -> {codigo}  total={objeto(pagina).get('total')} paginas={objeto(pagina).get('totalPaginas')}")
    codigo, fuera = pedir("GET", "/api/mantenimientos?pagina=999&tamano=20", token_admin)
    print(f"  pagina fuera de rango                 -> {codigo}  contenido={len(lista(objeto(fuera).get('contenido')))}  (esperado 0, sin error)")
    codigo, filtrado = pedir("GET", "/api/mantenimientos?tipo=CORRECTIVO", token_admin)
    print(f"  filtro tipo=CORRECTIVO                -> {codigo}  total={objeto(filtrado).get('total')}")
    codigo, filtrado = pedir("GET", "/api/mantenimientos?estado=FINALIZADO", token_admin)
    print(f"  filtro estado=FINALIZADO              -> {codigo}  total={objeto(filtrado).get('total')}")

    print("\n" + "=" * 68)
    print("FIN DE LA VERIFICACION")
    print("=" * 68)


if __name__ == "__main__":
    main()
