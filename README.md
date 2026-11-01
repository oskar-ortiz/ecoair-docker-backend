# Docker — EcoAir Backend

Backend Spring Boot de EcoAir, empaquetado en Docker y ejecutado con Docker Compose detrás de Nginx, con PostgreSQL persistente.

## Portfolio & Author

- Portfolio: https://oskar-ortiz.vercel.app
- GitHub: https://github.com/oskar-ortiz
- This implementation reflects a production-oriented container topology designed around network isolation, reverse proxying, resiliency, and persistence for a Java 17 backend service.

## Enterprise Architecture Overview

```mermaid
flowchart TD
    Client[External Client / Browser / API Consumer] -->|HTTP 80| Nginx[NGINX
Reverse Proxy
Public entry point
Network: edge]

    subgraph Edge[Bridge Network: edge]
        Nginx
    end

    Nginx -->|Proxy routing| Backend[Spring Boot Backend
Java 17
Application Services]

    subgraph Data[Bridge Network: data]
        Backend
        Postgres[(PostgreSQL 16
Persistent Data Layer)]
    end

    Backend -->|Private service communication| Postgres
    Postgres -->|Durable storage| Volume[(postgres_data
Named Docker Volume)]
```

### Design Principles
- Public access is restricted to a single ingress layer: Nginx on port 80.
- The application tier remains isolated in an internal bridge network, reducing the attack surface.
- PostgreSQL is decoupled from direct host exposure and persists through a dedicated named volume.
- This topology aligns with modern container patterns for secure deployment, maintainability, and operational resilience.

## Tecnologías

- Java 17, Spring Boot 3.3.5 y Maven
- Docker Compose
- Nginx como reverse proxy
- PostgreSQL 16
- H2 para ejecución local con Maven
- Spring Data JPA y Spring Boot Actuator

## Arquitectura

```text
Cliente --HTTP:80--> Nginx --red edge--> Backend Spring Boot
                                      Backend --red interna data--> PostgreSQL
```

Solo Nginx publica un puerto al host. El backend no publica `8080` y PostgreSQL no publica `5432`. Nginx y backend comparten la red Bridge `edge`; backend y PostgreSQL comparten la red Bridge interna `data` con acceso privado. La base de datos se persiste con el volumen `postgres_data` para evitar pérdida de datos en reinicios o recreaciones del contenedor.

## Architecture & Container Topology

```mermaid
flowchart TD
    Client[Client / External Consumer
HTTP over port 80] -->|HTTP ingress| Nginx[Nginx
Reverse Proxy
Port 80 exposed on host
Network: edge]

    subgraph Edge["Bridge Network: edge"]
        Nginx
    end

    Nginx -->|Proxy / routing| Backend[Spring Boot Backend
Java 17
Application API]

    subgraph Data["Bridge Network: data"]
        Backend
        Postgres[(PostgreSQL 16
Database Service)]
    end

    Backend -->|Private internal communication| Postgres
    Postgres -->|Persistent storage| Volume[(postgres_data
Named Docker Volume)]
```

### Topology Notes
- The client reaches the application only through Nginx on port 80.
- Nginx is the single public entry point and acts as the reverse proxy.
- The Spring Boot backend runs in the isolated internal network `data` and is not exposed directly to the host.
- PostgreSQL also remains isolated in the same private network and does not expose port 5432 to the host.
- The named volume `postgres_data` ensures persistence across restarts and container recreation events.
- This pattern reduces the attack surface, improves network isolation, and keeps the database layer private and controlled.

### Architecture Summary
- Public access: `HTTP 80` -> Nginx
- Internal access: `Backend <-> PostgreSQL` on private Docker network `data`
- Persistence: `postgres_data` mounted on PostgreSQL
- Security posture: only one service is exposed to the host; the rest remain private

## Iniciar con Docker Compose

Requiere Docker Desktop o Docker Engine con Docker Compose v2.

En PowerShell:

```powershell
Copy-Item .env.example .env
```

En macOS/Linux:

```bash
cp .env.example .env
```

Edita `.env` y cambia `POSTGRES_PASSWORD` antes de iniciar. La contraseña del ejemplo es solo para desarrollo local.

Construye y levanta los servicios:

```bash
docker compose up --build -d
docker compose ps
docker compose logs --tail=100
```

Compose espera a que PostgreSQL y el backend pasen sus healthchecks antes de arrancar el servicio dependiente.

## Verificar

Nginx es la entrada pública en el puerto 80:

```bash
curl http://localhost/actuator/health
curl http://localhost/api/alertas
```

La respuesta del health endpoint debe contener `"status":"UP"`. Para crear una alerta de prueba:

```bash
curl -X POST http://localhost/api/alertas \
  -H "Content-Type: application/json" \
  -d "{\"estacion\":\"Centro\",\"tipo\":\"PM2.5\",\"valor\":18.4,\"severidad\":\"MODERADA\",\"fecha\":\"2026-10-22T12:00:00Z\"}"
```

Consultar nuevamente `http://localhost/api/alertas` debe mostrar la alerta creada.

## Operación y persistencia

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f nginx
docker compose restart
docker compose down
```

`docker compose down` elimina los contenedores y redes del proyecto, pero conserva `postgres_data`. No uses `docker compose down -v` salvo que quieras borrar permanentemente la base de datos. Despliega de forma segura con `docker compose up -d` y verifica con `docker compose ps` y `docker compose logs -f`.

Los límites de memoria se pueden ajustar en `.env` (`BACKEND_MEMORY_LIMIT`, `POSTGRES_MEMORY_LIMIT`, `NGINX_MEMORY_LIMIT`). La auditoría local observó mayor margen con `384M` para el backend que soporta adecuadamente la carga; PostgreSQL y Nginx conservan recursos para mantener estabilidad del sistema en un entorno acotado.

## Ejecución local sin Docker

Requiere Java 17 y Maven. Esta ejecución utiliza H2 en memoria:

```bash
mvn spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.

## Diferencias frente al flujo anterior

| Antes | Con Docker Compose |
|---|---|
| `docker build` y `docker run` manuales para un único backend | Compose construye y coordina Nginx, backend y PostgreSQL |
| El backend publica `8080` al host | Solo Nginx publica `80`; backend y PostgreSQL no tienen puertos publicados |
| H2 en memoria pierde datos al reiniciar | PostgreSQL usa el volumen `postgres_data` |
| Sin proxy ni redes separadas | Nginx reenvía solicitudes y la base de datos queda en una red interna |
| Verificación manual del contenedor | Healthchecks y dependencias de arranque declaradas en Compose |

La ejecución anterior se conserva en el informe como baseline. Los nuevos resultados de arranque, memoria y salud deben interpretarse para esta topología con PostgreSQL y Nginx.
