"""
Reproduce el 400 que devuelve POST /api/tipos-activo y muestra el cuerpo
completo de la respuesta.

Sirve para distinguir un problema de validacion (400) de un problema de
permisos (403).
"""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8081/api"


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
            return e.code, json.loads(crudo)
        except json.JSONDecodeError:
            return e.code, crudo


estado, datos = pedir("/auth/login", "POST",
                       {"correo": "admin@unh.edu.pe", "clave": "Admin1234"})
token = datos["token"]

print("POST /api/tipos-activo con {nombre, categoria}")
codigo, cuerpo = pedir("/tipos-activo", "POST",
                        {"nombre": "No permitido", "categoria": "EQUIPO"}, token=token)
print("  ->", codigo, json.dumps(cuerpo, ensure_ascii=False))

print("\nPOST /api/tipos-activo con el body completo")
codigo, cuerpo = pedir("/tipos-activo", "POST",
                        {"nombre": "No permitido 2", "categoria": "EQUIPO",
                         "requiereMantenimiento": True}, token=token)
print("  ->", codigo, json.dumps(cuerpo, ensure_ascii=False))

print("\nPOST /api/ubicaciones (comparacion)")
codigo, cuerpo = pedir("/ubicaciones", "POST",
                        {"nombre": "No permitido", "tipoUbicacion": "SALA"}, token=token)
print("  ->", codigo, json.dumps(cuerpo, ensure_ascii=False))
