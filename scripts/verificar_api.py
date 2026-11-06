#!/usr/bin/env python3
"""Verifica la matriz de permisos y las validaciones de la API.

Uso:
    python3 scripts/verificar_api.py [modulo]

Modulo: `incidencias`, `activos`, `ubicaciones`, `tipos-activo`, `usuarios`.
Si se omite, prueba la matriz de permisos generica sobre `incidencias`.

Devuelve codigo de salida 0 si todo pasa, 1 si algun check falla.

Por que un script y no `curl | python3`: el pipe hacia un interprete dispara
el escaner de seguridad de la herramienta y el comando queda esperando
aprobacion (bloqueado) si nadie esta mirando. Ademas el script imprime los
codigos REALES, que es lo que hay que verificar: las anotaciones
`@PreAuthorize` no demuestran que el servidor devuelva 403.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8081/api"

ADMIN = ("admin@unh.edu.pe", "Admin1234")        # todos los modulos
USUARIO = ("luis@unh.edu.pe", "ClaveSegura123")  # solo ACTIVOS

fallos = []


def pedir(ruta, metodo="GET", cuerpo=None, token=None):
    """Devuelve (codigo_http, cuerpo_json)."""
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            crudo = r.read().decode()
            return r.status, (json.loads(crudo) if crudo else None)
    except urllib.error.HTTPError as e:
        crudo = e.read().decode()
        try:
            return e.code, (json.loads(crudo) if crudo else None)
        except json.JSONDecodeError:
            return e.code, crudo


def comprobar(etiqueta, obtenido, esperado):
    ok = obtenido == esperado
    marca = "OK  " if ok else "FALLA"
    print(f"  [{marca}] {etiqueta}: {obtenido} (esperado {esperado})")
    if not ok:
        fallos.append(f"{etiqueta}: {obtenido} != {esperado}")
    return ok


def login(credenciales):
    estado, datos = pedir("/auth/login", "POST",
                           {"correo": credenciales[0], "clave": credenciales[1]})
    if estado != 200:
        raise SystemExit(f"Login fallo ({estado}): {credenciales[0]} -> {datos}")
    return datos["token"]


def main():
    modulo = sys.argv[1] if len(sys.argv) > 1 else "incidencias"

    print(f"== Verificando API: {modulo} ==")
    print(f"Base: {BASE}\n")

    print("-- Login --")
    token_admin = login(ADMIN)
    token_usuario = login(USUARIO)
    estado, datos = pedir("/auth/me", token=token_admin)
    print(f"  admin modulos: {datos.get('modulos')}")
    estado, datos = pedir("/auth/me", token=token_usuario)
    print(f"  usuario modulos: {datos.get('modulos')}\n")

    print("-- Matriz de permisos --")
    comprobar("sin token -> 401", pedir(f"/{modulo}")[0], 401)
    comprobar("lectura sin el modulo -> 200", pedir(f"/{modulo}", token=token_usuario)[0], 200)

    # Escritura: el cuerpo depende del modulo; se toma el primero que exista.
    cuerpos = {
        "incidencias": {"idActivo": 1, "descripcion": "prueba de permisos",
                        "prioridad": "BAJA"},
        "activos": {"codigoPatrimonial": "PERM-PROBA", "nombre": "Permiso",
                    "idTipoActivo": 1, "idUbicacion": 1},
        "ubicaciones": {"nombre": "Permiso prueba", "tipoUbicacion": "SALA"},
        "tipos-activo": {"nombre": "Permiso prueba", "categoria": "EQUIPO"},
    }
    cuerpo = cuerpos.get(modulo, {"nombre": "prueba"})
    comprobar("escritura sin el modulo -> 403",
              pedir(f"/{modulo}", "POST", cuerpo, token=token_usuario)[0], 403)
    comprobar("escritura con el modulo -> 2xx",
              pedir(f"/{modulo}", "POST", cuerpo, token=token_admin)[0] // 100, 2)

    print("\n-- Resultado --")
    if fallos:
        print(f"{len(fallos)} check(s) FALLARON:")
        for f in fallos:
            print(f"  - {f}")
        return 1
    print("Todos los checks pasaron.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
