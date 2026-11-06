"""Depura el 401 en POST /api/activos: imprime el cuerpo real de la respuesta."""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8081"


def pedir(ruta, metodo="GET", cuerpo=None, token=None):
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


estado, datos = pedir("/api/auth/login", "POST",
                       {"correo": "admin@unh.edu.pe", "clave": "Admin1234"})
print("login:", estado)
token = datos["token"]
print("token (primeros 40):", token[:40])

print("\nGET /api/activos/opciones con token:")
estado, cuerpo = pedir("/api/activos/opciones", token=token)
print(" ->", estado, type(cuerpo).__name__)

print("\nGET /api/auth/me con token:")
estado, cuerpo = pedir("/api/auth/me", token=token)
print(" ->", estado, cuerpo)

print("\nPOST /api/activos con token:")
estado, cuerpo = pedir("/api/activos", "POST", {
    "codigoPatrimonial": "DBG-001",
    "nombre": "Activo de depuracion",
    "idTipoActivo": 1,
    "idUbicacion": 1,
    "estadoOperativo": "OPERATIVO",
}, token=token)
print(" ->", estado, json.dumps(cuerpo, indent=2, ensure_ascii=False) if cuerpo else "")

print("\nPOST /api/mantenimientos/preventivo con token:")
estado, cuerpo = pedir("/api/mantenimientos/preventivo", "POST", {
    "idActivo": 2,
    "observaciones": "debug",
}, token=token)
print(" ->", estado, json.dumps(cuerpo, indent=2, ensure_ascii=False) if cuerpo else "")
