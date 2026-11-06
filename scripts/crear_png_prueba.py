"""Crea un PNG minimo valido para probar la subida de evidencias."""

import base64

PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

with open("/tmp/evidencia_prueba.png", "wb") as archivo:
    archivo.write(PNG_1X1)

print(f"PNG creado: {len(PNG_1X1)} bytes")
