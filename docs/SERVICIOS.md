# Sistema de Mantenimiento UNH — arranque como servicios (systemd de usuario)

Los servicios quedan como unidades de systemd de usuario. Con `Linger=yes`
ya activo en la maquina, arrancan al iniciar sesion y sobreviven al cierre
de sesion: no dependen de que haya una terminal abierta.

## Estado

```
mantenimiento-backend.service   -> Spring Boot en el puerto 8081
mantenimiento-frontend.service   -> Vite en el puerto 5174
```

Los dos estan `enabled` (arrancan solos) y `active`.

## Comandos

```bash
# Estado
systemctl --user status mantenimiento-backend mantenimiento-frontend

# Reiniciar el backend (tras cambiar codigo Java, OJO: hay que re-empaquetar)
systemctl --user restart mantenimiento-backend

# Ver los logs en vivo
journalctl --user -u mantenimiento-backend -f
# o los archivos:
tail -f ~/.local/state/mantenimiento/backend.log

# Parar / arrancar
systemctl --user stop mantenimiento-backend
systemctl --user start mantenimiento-backend
```

## IMPORTANTE: el backend corre el JAR, no el codigo fuente

El servicio ejecuta:

```
java -jar backend/target/mantenimiento-0.0.1-SNAPSHOT.jar
```

Asi que **despues de cambiar codigo Java hay que re-empaquetar** o el
servicio seguira sirviendo el codigo viejo:

```bash
cd backend
./mvnw -B package -DskipTests     # genera el jar
systemctl --user restart mantenimiento-backend
```

Alternativa durante el desarrollo: en vez del servicio, levantar a mano
`./mvnw spring-boot:run` (recarga el codigo al recompilar, pero no hay
devtools asi que hay que reiniciar a mano).

## El frontend si recarga solo

Vite tiene HMR: guardas el archivo y el navegador se actualiza. No hay que
reiniciar el servicio para cambios en el frontend.

## Archivos

| Archivo | Rol |
|---|---|
| `~/.config/systemd/user/mantenimiento-backend.service` | Unidad del backend |
| `~/.config/systemd/user/mantenimiento-frontend.service` | Unidad del frontend |
| `~/.local/state/mantenimiento/backend.log` | Log del backend |
| `~/.local/state/mantenimiento/frontend.log` | Log del frontend |

## Credenciales

La clave de la base de datos **no** esta en el archivo de servicio: esta en el
`.env` de la raiz del repo, que se carga solo con
`spring.config.import` y esta en `.gitignore`.

Usuarios de prueba: `admin@unh.edu.pe` / `Admin1234` (todos los modulos) y
`luis@unh.edu.pe` / `ClaveSegura123` (solo `ACTIVOS`).

## Nota sobre los puertos

El backend usa el 8081 y el frontend el 5174 porque el 8080 y el 5173 ya los
usa otro proyecto de la maquina. No tocarlos.
