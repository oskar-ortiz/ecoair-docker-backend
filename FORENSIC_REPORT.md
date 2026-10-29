# Informe forense — Taller Docker EcoAir

**Estudiante:** 905523

**Fecha de ejecución:** 2026-10-20 (hora local, UTC-05:00)

**Motor:** Docker Desktop 4.91.0, Linux/amd64, cgroup v2
**Daemon ID:** `bae1e2b7-bdf6-48a8-8530-a671cb4b6c54`

La salida completa, en orden cronológico, de los comandos ejecutados y sus timestamps se conserva en [`audit_session.log`](./audit_session.log). Este informe resume los resultados medidos directamente en el daemon local; no se sustituyeron salidas.

## Identidad final

| Campo | Evidencia |
|---|---|
| Imagen | `eco-air-backend:1.0.0` |
| ID de imagen (`docker inspect --format='{{.Id}}'`) | `sha256:9910633fcdb9e34c5f0d3333bdb1f5d1241fa453b461f5d412839f7d3c22aae4` |
| Digest de configuración reportado por BuildKit | `sha256:bf80f39a1bcc8c664bddbcdf592133e4f07368f5db0a6021999e80bb647115cd` |
| `OPERATOR_ID` en el contenedor | `905523` |
| Contenedor final | `eco_air_active`, ID `44ded8cbb97243a3920036952f390b7fdc9b95a56028bdf21c6072c85886351b` |
| Estado final | `running`, límite `134217728` bytes (128 MiB), puerto `8080:8080` |
| Health endpoint | `{"status":"UP"}` |

La imagen se reconstruyó después de normalizar Dockerfile y `.dockerignore` a LF sin BOM. BuildKit conservó el digest de configuración y las capas Maven/JAR en caché, pero emitió un digest de manifiesto nuevo; por ello se recreó el contenedor final desde la etiqueta actual y se verificó de nuevo su hash, arranque y health.

### `docker stats --no-stream` final

Timestamp local del comando: `2026-10-20T21:02:40.0434000-05:00`.

```text
CONTAINER ID   NAME             CPU %     MEM USAGE / LIMIT   MEM %     NET I/O         BLOCK I/O       PIDS
44ded8cbb972   eco_air_active   0.63%     122MiB / 128MiB     95.33%    2.99kB / 1.77kB  144MB / 187MB   31
```

La lectura de memoria es alta pero se mantuvo en el rango observado durante las pruebas a 128 MiB. En la verificación final, después de varios minutos, el contenedor seguía `running`, el endpoint devolvió `UP` y el inicio final apareció en `9.824` segundos.

## Reto 2 — capas y usuario

### Historial de imagen

Timestamp: `2026-10-20T20:47:43.9084671-05:00`

Comando: `docker history --human --format "table {{.Size}}\t{{.CreatedBy}}" eco-air-backend:1.0.0`
Código de salida: `0`.

```text
SIZE      CREATED BY
0B        ENTRYPOINT ["/bin/sh" "-c" "exec java $JAVA_…
0B        USER sensor_user
0B        EXPOSE [8080/tcp]
0B        ENV OPERATOR_ID=905523
0B        ENV JAVA_TOOL_OPTIONS=-XX:+UseContainerSuppo…
50.1MB    RUN |1 STUDENT_ID=905523 /bin/sh -c chown -R…
50.1MB    COPY /build/target/eco-air-backend-1.0.0.jar…
8.19kB    WORKDIR /app
41kB      RUN |1 STUDENT_ID=905523 /bin/sh -c addgroup…
0B        ARG STUDENT_ID=905523
0B        ENTRYPOINT ["/__cacert_entrypoint.sh"]
12.3kB    COPY --chmod=755 entrypoint.sh /__cacert_ent…
12.3kB    RUN /bin/sh -c set -eux;     echo "Verifying…
141MB     RUN /bin/sh -c set -eux;     ARCH="$(apk --…
0B        ENV JAVA_VERSION=jdk-17.0.20.1+1
44MB      RUN /bin/sh -c set -eux;     apk add --no-ca…
0B        ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_AL…
0B        ENV PATH=/opt/java/openjdk/bin:/usr/local/sb…
0B        ENV JAVA_HOME=/opt/java/openjdk
0B        CMD ["/bin/sh"]
9.08MB    ADD alpine-minirootfs-3.24.2-x86_64.tar.gz /…
```

### Usuario, permisos y contenido de runtime

| Timestamp local | Comando | Salida exacta / código | Interpretación |
|---|---|---|---|
| `20:47:44.2769842-05:00` | `docker run -d --name eco_air_active -p 8080:8080 eco-air-backend:1.0.0` | `0e5f6b44a128664230860a99255ec29b8057b4de249d3b8ab0560b71b9595061` (0) | Primera ejecución de comprobación; luego se recreó con el límite calibrado. |
| `20:47:58.0195000-05:00` | `docker top eco_air_active -o pid,user,comm` | `PID USER COMMAND` / `875 10001 java` (0) | Docker muestra UID numérico. |
| `20:47:58.1960803-05:00` | `docker exec eco_air_active id` | `uid=10001(sensor_user) gid=101(sensorgroup) groups=101(sensorgroup)` (0) | Confirma que UID 10001 corresponde a `sensor_user`. |
| `20:47:58.3970159-05:00` | `docker exec eco_air_active touch /etc/crontab_fake` | `touch: cannot touch '/etc/crontab_fake': Permission denied` (1) | El proceso no puede escribir en el área protegida del sistema. |
| `20:47:58.5526451-05:00` | `docker exec eco_air_active which mvn` | Sin salida (1) | Maven no está instalado en la imagen runtime. |
| `20:47:58.6969342-05:00` | `docker exec eco_air_active sh -c 'find / -name "*.java" 2>/dev/null'` | Sin salida (1) | No se encontraron fuentes `.java`; el `find` devolvió 1 al encontrar rutas inaccesibles con stderr suprimido. |
| `20:47:59.5337729-05:00` | `docker logs --timestamps eco_air_active` | Incluye `Started EcoAirApplication in 6.685 seconds (process running for 7.579)` | El backend inició bajo `sensor_user`. |
| `20:47:59.6240716-05:00` | `docker inspect --format='{{.State.Status}} {{.State.ExitCode}} {{.State.OOMKilled}}' eco_air_active` | `running 0 false` (0) | La primera ejecución permaneció activa. |

## Reto 3 — OOM y calibración cgroups

### Límite de 48 MiB

| Timestamp local | Comando | Salida exacta / resultado |
|---|---|---|
| `20:48:15.8734397-05:00` | `docker run -d --name eco_air_crash -m 48m eco-air-backend:1.0.0` | ID `24aeb41f581e3a5f72bdbee483835d134598bb702b2011a50c4ed12bbd8b2eff`, código 0. |
| `20:48:26.4326212-05:00` | `docker inspect eco_air_crash --format='Status: {{.State.Status}} \| ExitCode: {{.State.ExitCode}} \| OOMKilled: {{.State.OOMKilled}}'` | `Status: running \| ExitCode: 0 \| OOMKilled: false` a los 10 s. |
| `20:48:26.6629164-05:00` | `docker stats --no-stream eco_air_crash` | `24aeb41f581e eco_air_crash 35.57% 47.91MiB / 48MiB 99.81% 872B / 126B 300MB / 344MB 15`. |
| `20:48:46.2226466-05:00` | Mismo `docker inspect` | `Status: exited \| ExitCode: 137 \| OOMKilled: true` después de esperar 20 s. |
| `20:48:46.4206386-05:00` | Mismo `docker stats --no-stream` | `24aeb41f581e eco_air_crash 0.00% 0B / 0B 0.00% 0B / 0B 0B / 0B 0`. |

Resultado observado: el límite de memoria de 48 MiB causó terminación por OOM (código 137). No se modificó el estado para producir ese resultado.

### Tabla de calibración

Cada intento se recreó bajo el nombre `eco_air_crash`; se esperó al menos 30 s en los límites bajos para detectar una terminación tardía. El tiempo corresponde al valor `Started EcoAirApplication in ... seconds` de los logs de Spring Boot. Memoria es la salida real de `docker stats --no-stream`.

| Límite | Resultado observado | Arranque | Stats observadas |
|---:|---|---:|---|
| 48 MiB | `exited`, código 137, `OOMKilled=true`; a los 10 s seguía iniciando | No llegó a `Started` | `47.91MiB / 48MiB`, 99.81%; tras OOM, `0B / 0B`. |
| 112 MiB | `running`, no OOM en la observación; incumple el criterio de menos de 15 s | 16.508 s | `106.6MiB / 112MiB`, 95.20%. |
| 128 MiB | `running`, cumplió el umbral de arranque; lectura posterior estable y health `UP` | 9.294 s; validación final con la imagen recompuesta: 9.824 s | `121.8MiB / 128MiB`, 95.18%; luego `122.3MiB / 128MiB`, 95.57%. Contenedor final: `122MiB / 128MiB`, 95.33%. |
| 144 MiB | `running` | 7.559 s | `126.6MiB / 144MiB`, 87.92%; luego `126.9MiB / 144MiB`, 88.09%. |
| 160 MiB | `running` | 7.064 s | `143.3MiB / 160MiB`, 89.58%; luego `143.5MiB / 160MiB`, 89.69%. |
| 192 MiB | `running` | 6.132 s | `175.3MiB / 192MiB`, 91.33% en dos lecturas. |
| 256 MiB | `running` | 6.838 s | `178.2MiB / 256MiB`, 69.61%. |
| 384 MiB | `running` | 7.948 s | `178.7MiB / 384MiB`, 46.54%; luego `177.6MiB / 384MiB`, 46.26%. |
| 512 MiB | `running` | 5.941 s | `179.6MiB / 512MiB`, 35.08% en dos lecturas. |

**Umbral mínimo elegido: 128 MiB.** Fue el límite menor probado que produjo `Started EcoAirApplication` en menos de 15 s y mantuvo el proceso activo en las lecturas siguientes. A 112 MiB el proceso seguía `running`, pero el arranque tardó 16.508 s y por eso no cumple el requisito. El contenedor final se dejó corriendo con `-m 128m -p 8080:8080`; su última lectura registró 123.1 MiB de 128 MiB y el endpoint de salud respondió `{"status":"UP"}`.

## Entorno y preservación de otros contenedores

Al inicio `docker version` y `docker info` confirmaron Docker Desktop y el daemon Linux. El primer build falló mientras Docker Desktop se detenía, con HTTP 500 al obtener BuildKit. Se registraron el error y la recuperación del Desktop en el log; el build posterior completó correctamente. Los contenedores existentes `acme-mq-prod-01` y `kernel-demo` no se eliminaron ni modificaron. La limpieza y recreación se limitaron a `eco_air_active` y `eco_air_crash`.

## Reto 4 — Docker Compose, reverse proxy y persistencia PostgreSQL

**Fecha de ejecución:** 2026-10-22 (hora local, UTC-05:00). Evidencia de comandos y resultados de esta ejecución: [`audit_session.log`](./audit_session.log).

La auditoría de esta sección corresponde a la nueva topología Compose y no sustituye el baseline previo de H2.

### Topología verificada

| Servicio | Imagen / configuración | Resultado observado |
|---|---|---|
| `nginx` | `nginx:stable`, publicado `80:80`, conectado solo a `edge` | `healthy`; healthcheck Nginx correcto; único puerto de servicio publicado al host. |
| `backend` | Imagen construida localmente, usuario `sensor_user` (UID 10001), límite `384 MiB`, puerto interno `8080` | `healthy`; `8080/tcp` sin publicación al host; conectado a `edge` y `data`. |
| `postgres` | `postgres:16-bookworm`, límite `512 MiB`, volumen `taller-docker-_postgres_data` | `healthy`; `5432/tcp` sin publicación al host; conectado solo a `data`. |
| Red `edge` | Bridge no interna | Contiene Nginx y backend. |
| Red `data` | Bridge con `internal=true` | Contiene backend y PostgreSQL; no contiene Nginx. |

Se usó PostgreSQL Bookworm porque la imagen Alpine obtenida durante esta auditoría no contenía el usuario de sistema `postgres` que requiere su entrypoint, por lo que no completaba el inicio. La imagen `nginx:alpine` del daemon falló al ejecutar su entrypoint (`exec format error`); la sustitución por `nginx:stable` inició correctamente y pasó el healthcheck.

### Verificaciones funcionales y de persistencia

| Prueba | Resultado |
|---|---|
| `docker compose config --quiet` | Correcto. |
| `mvn test` | `BUILD SUCCESS`; el proyecto no contiene fuentes de prueba. |
| `docker compose up -d --wait --wait-timeout 180` | Los tres servicios pasaron a `healthy`. |
| `GET http://localhost/actuator/health` a través de Nginx | HTTP 200; `{"status":"UP"}`. |
| `GET http://localhost/api/alertas` a través de Nginx | HTTP 200; devolvió las alertas persistidas. |
| `POST /api/alertas` de prueba | HTTP correcto; creó la alerta de auditoría con ID `3`. |
| `docker compose down` seguido de `docker compose up -d --wait --wait-timeout 180` | Los servicios volvieron a `healthy`; el volumen nombrado siguió presente. |
| Consulta de la alerta después de recrear los servicios | La alerta `AuditoriaPersistencia`, ID `3`, siguió disponible. |
| Identidad/permisos del backend | `sensor_user`; el proceso no se ejecuta como root. |
| Publicación de puertos | Solo Nginx publica `80`; backend `8080` y PostgreSQL `5432` no tienen puertos de host asignados. |

### Memoria y arranque de la topología Compose

La primera ejecución del backend con PostgreSQL usó el límite de `256 MiB`: Spring Boot inició en `36.092` segundos y se observó una lectura de `229.3 MiB / 256 MiB` durante las comprobaciones iniciales. El backend permaneció healthy, pero el margen era reducido. Se fijó `384 MiB` como límite local de Compose para evitar operar tan cerca del techo; en la recreación final el arranque de Spring Boot tomó `12.256` segundos.

En la lectura final de `docker stats --no-stream`, tomada después de recrear los servicios y confirmar la persistencia, se observó:

| Servicio | Uso observado / límite | Estado |
|---|---:|---|
| Backend | `188.7 MiB / 384 MiB` | `healthy` |
| PostgreSQL | `37.91 MiB / 512 MiB` | `healthy` |
| Nginx | `4.816 MiB / 64 MiB` | `healthy` |

El límite recomendado en `.env` para el backend queda en `384M`; es una configuración conservadora observada en esta máquina, no una garantía para otros hosts. El límite anterior de `128 MiB` se midió con H2 y no debe trasladarse a la topología PostgreSQL sin repetir la calibración en el entorno de entrega.

### Configuración local

Se generó un `.env` local ignorado por Git con una contraseña aleatoria y se actualizó la contraseña del rol PostgreSQL existente antes de recrear el stack. El secreto no se incluye en este informe ni en el registro de auditoría. La plantilla `.env.example` conserva únicamente un valor de demostración que debe cambiarse antes de usarla en otra instalación.

## Reto 5 — Modularización del backend y perfiles CORS

**Fecha de validación:** 2026-10-29 (hora local, UTC-05:00).

El backend Maven, el Dockerfile, `.dockerignore` y Nginx se reorganizaron bajo `backend/`. Compose y las plantillas de entorno permanecen en la raíz. El Compose construye con `context: ./backend`; la configuración de Nginx se monta desde `./backend/nginx/default.conf`. El volumen de desarrollo conserva el nombre histórico `taller-docker-_postgres_data`; el perfil productivo usa un volumen configurable distinto.

### Perfiles y política CORS

| Perfil / prueba | Resultado observado |
|---|---|
| `dev`, `OPTIONS /api/alertas` con `Origin: http://localhost:3000`, método `POST`, headers `content-type,authorization` | HTTP 200; `Access-Control-Allow-Origin` coincide exactamente con localhost:3000 y `Access-Control-Allow-Credentials: true`. |
| `dev`, mismo preflight con `Origin: https://untrusted.example` | HTTP 403; sin autorización CORS. |
| `prod`, `CORS_ALLOWED_ORIGINS` vacío | El perfil y los tres servicios iniciaron saludables; el preflight fue rechazado y no se emitió `Access-Control-Allow-Origin`. |
| `prod`, origen configurado `https://app.ecoair.example` en entorno de prueba aislado | HTTP 200 y permiso de credenciales para ese origen exacto; `https://untrusted.example` no recibió `Access-Control-Allow-Origin`. El stack y volumen de prueba aislados se eliminaron al finalizar. |

Las credenciales CORS solo se habilitan con una lista explícita de orígenes; no se usa `*`. La configuración ahora también rechaza `*` al iniciar mediante una prueba unitaria. La API no implementa autenticación ni roles: CORS no sustituye autorización.

### Validación tras la reorganización

- `mvn test` desde `backend/`: `BUILD SUCCESS`; 1 prueba unitaria, sin fallos.
- `docker compose --env-file .env config --quiet` y validación de la plantilla de producción: correctas.
- Construcción de imagen desde `backend/` y healthcheck HTTP a través de Nginx: correctos.
- Al cierre, el stack de desarrollo quedó detenido como estaba antes de esta validación; se preservó el volumen PostgreSQL existente.
