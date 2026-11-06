"""
Verificacion del incremento I6 (Evidencias) por API real.

Habla HTTP con urllib y devuelve codigos de estado reales.

Uso: python3 scripts/verificar_i6.py
"""

import json
import urllib.error
import urllib.request
import uuid
from datetime import datetime

BASE = "http://localhost:8081"

ADMIN = ("admin@unh.edu.pe", "Admin1234")
# Usuario con el modulo ACTIVOS pero SIN EVIDENCIAS.
SIN_MODULO = ("luis@unh.edu.pe", "ClaveSegura123")


def pedir(metodo, ruta, token=None, cuerpo=None, archivos=None, binario=False):
    """Devuelve (codigo, cuerpo)."""
    Boundary = "----verificarI6" + uuid.uuid4().hex
    peticion = urllib.request.Request(BASE + ruta, method=metodo)
    if token:
        peticion.add_header("Authorization", "Bearer " + token)

    datos = None
    if archivos is not None:
        partes = []
        for nombre, nombre_archivo, contenido, tipo in archivos:
            partes.append(
                f"--{Boundary}\r\n"
                f'Content-Disposition: form-data; name="{nombre}"; '
                f'filename="{nombre_archivo}"\r\n'
                f"Content-Type: {tipo}\r\n\r\n".encode()
                + contenido
                + b"\r\n"
            )
        for nombre, valor in (cuerpo or {}).items():
            partes.append(
                f"--{Boundary}\r\n"
                f'Content-Disposition: form-data; name="{nombre}"\r\n\r\n'
                f"{valor}\r\n".encode()
            )
        partes.append(f"--{Boundary}--\r\n".encode())
        datos = b"".join(partes)
        peticion.add_header("Content-Type", f"multipart/form-data; boundary={Boundary}")
    elif cuerpo is not None:
        datos = json.dumps(cuerpo).encode()
        peticion.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(peticion, datos) as respuesta:
            texto = respuesta.read()
            if binario:
                return respuesta.status, texto
            return respuesta.status, (json.loads(texto) if texto else {})
    except urllib.error.HTTPError as error:
        texto = error.read()
        if binario:
            return error.code, texto
        try:
            return error.code, json.loads(texto)
        except ValueError:
            return error.code, texto.decode("utf-8", "replace")[:300]


def objeto(respuesta):
    return respuesta if isinstance(respuesta, dict) else {}


def lista(respuesta):
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


PNG = bytes([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
])


def main():
    token_admin = entrar(*ADMIN)
    token_sin = entrar(*SIN_MODULO)
    titulo("1. MATRIZ DE PERMISOS DE EVIDENCIAS")
    codigo, _ = pedir("GET", "/api/evidencias/mantenimiento/1")
    print(f"  lectura sin token                    -> {codigo}  (esperado 401)")
    codigo, _ = pedir("GET", "/api/evidencias/mantenimiento/1", token_sin)
    print(f"  lectura usuario SIN modulo          -> {codigo}  (esperado 403)")
    codigo, _ = pedir("POST", "/api/evidencias/mantenimiento/1", token_sin,
                      archivos=[("archivo", "x.png", PNG, "image/png")])
    print(f"  escritura usuario SIN modulo         -> {codigo}  (esperado 403)")
    codigo, _ = pedir("GET", "/api/evidencias/1/descargar", token_sin)
    print(f"  descarga usuario SIN modulo          -> {codigo}  (esperado 403)")

    titulo("2. SUBIDA DE UNA EVIDENCIA")
    # Un mantenimiento propio: los datos de dev tienen activos desactivados.
    codigo, tipos = pedir("GET", "/api/tipos-activo", token_admin)
    codigo, ubicaciones = pedir("GET", "/api/ubicaciones", token_admin)
    tipo_id = lista(tipos)[0]["id"]
    ubicacion_id = lista(ubicaciones)[0]["id"]

    codigo, activo = pedir("POST", "/api/activos", token_admin, {
        "codigoPatrimonial": f"I6-PRUEBA-{uuid.uuid4().hex[:8]}",
        "nombre": "Activo para evidencia",
        "idTipoActivo": tipo_id,
        "idUbicacion": ubicacion_id,
        "estadoOperativo": "OPERATIVO",
    })
    activo_id = objeto(activo).get("id")

    codigo, mantenimiento = pedir("POST", "/api/mantenimientos/preventivo", token_admin, {
        "idActivo": activo_id,
        "observaciones": "Trabajo con evidencia",
    })
    print(f"  crear activo                         -> {codigo}  (esperado 201)")
    print(f"  crear mantenimiento                  -> {codigo}  (esperado 201)")
    mant_id = objeto(mantenimiento).get("id")

    codigo, evidencia = pedir("POST", f"/api/evidencias/mantenimiento/{mant_id}",
                              token_admin,
                              archivos=[("archivo", "foto de prueba.png", PNG, "image/png")])
    print(f"  subir evidencia                      -> {codigo}  (esperado 201)")
    evidencia = objeto(evidencia)
    if codigo != 201:
        print(f"  cuerpo del error: {evidencia}")
    print(f"  nombre original                      -> {evidencia.get('nombreOriginal')}")
    print(f"  nombre en disco (generado)           -> {evidencia.get('nombreArchivo')}")
    print(f"  tipo                                 -> {evidencia.get('tipoArchivo')}")
    evidencia_id = evidencia.get("id")

    titulo("3. LISTADO Y DESCARGA")
    codigo, pagina = pedir("GET", f"/api/evidencias/mantenimiento/{mant_id}", token_admin)
    print(f"  listar evidencias                    -> {codigo}  total={objeto(pagina).get('total')}")

    if evidencia_id:
        codigo, descargado = pedir("GET", f"/api/evidencias/{evidencia_id}/descargar",
                                   token_admin, binario=True)
        print(f"  descargar (con modulo)               -> {codigo}  (esperado 200)")
        if codigo == 200:
            print(f"  bytes descargados                    -> {len(descargado)}  (esperado 16)")
            print(f"  coincide con el archivo subido       -> {descargado == PNG}")
    codigo, _ = pedir("GET", "/api/evidencias/999999/descargar", token_admin)
    print(f"  descargar evidencia inexistente      -> {codigo}  (esperado 404)")

    titulo("4. VALIDACIONES DE LA SUBIDA")
    codigo, cuerpo = pedir("POST", f"/api/evidencias/mantenimiento/{mant_id}", token_admin,
                           archivos=[("archivo", "virus.exe", b"contenido", "application/x-msdownload")])
    print(f"  tipo no permitido (.exe)             -> {codigo}  (esperado 422)")
    print(f"  motivo                               -> {objeto(cuerpo).get('mensaje')}")

    codigo, cuerpo = pedir("POST", f"/api/evidencias/mantenimiento/{mant_id}", token_admin,
                           archivos=[("archivo", "vacio.png", b"", "image/png")])
    print(f"  archivo vacio                        -> {codigo}  (esperado 422)")

    codigo, cuerpo = pedir("POST", "/api/evidencias/mantenimiento/999999", token_admin,
                           archivos=[("archivo", "x.png", PNG, "image/png")])
    print(f"  mantenimiento inexistente            -> {codigo}  (esperado 404)")

    titulo("5. ELIMINACION")
    if evidencia_id:
        codigo, _ = pedir("DELETE", f"/api/evidencias/{evidencia_id}", token_admin)
        print(f"  eliminar evidencia                   -> {codigo}  (esperado 204)")
    codigo, pagina = pedir("GET", f"/api/evidencias/mantenimiento/{mant_id}", token_admin)
    print(f"  listar tras eliminar                 -> {codigo}  total={objeto(pagina).get('total')}  (esperado 0)")

    print("\n" + "=" * 68)
    print("FIN DE LA VERIFICACION")
    print("=" * 68)
    print(f"(evidencia de prueba creada el {datetime.now().isoformat(timespec='seconds')})")


if __name__ == "__main__":
    main()
