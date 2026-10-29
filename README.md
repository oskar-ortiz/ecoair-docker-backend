# Taller Docker — EcoAir Backend

Backend Spring Boot de EcoAir, organizado como módulo independiente y ejecutado con Docker Compose detrás de Nginx y PostgreSQL.

## Estructura

```text
.
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   ├── nginx/default.conf
│   └── src/main/
│       ├── java/com/ecoair/
│       │   ├── config/CorsConfig.java
│       │   └── alerta/
│       └── resources/
│           ├── application.properties
│           ├── application-dev.properties
│           └── application-prod.properties
├── docker-compose.yml
├── .env.dev.example
└── .env.prod.example
```

El proyecto solo incluye backend; no se agrega frontend. La API actual de alertas es `/api/alertas`.

## Ambientes y perfiles

Docker Compose usa un solo archivo de orquestación y selecciona el perfil de Spring Boot mediante `SPRING_PROFILES_ACTIVE` en el archivo de entorno:

- `dev`: CORS permite `http://localhost:3000` y `http://localhost:5173` por defecto. La aplicación local sin Docker continúa usando H2; Compose conecta el backend con PostgreSQL.
- `prod`: CORS no permite orígenes cruzados hasta configurar explícitamente `CORS_ALLOWED_ORIGINS` con el origen HTTPS real del frontend.

No uses `*` con credenciales CORS. Las credenciales están habilitadas para los orígenes listados; sin un origen permitido, el navegador no comparte la respuesta con el cliente. **CORS no sustituye autenticación ni autorización**: esta API todavía no implementa login ni roles.

### Desarrollo con Compose

En una instalación nueva, copia la plantilla de desarrollo y cambia la contraseña de ejemplo:

```powershell
Copy-Item .env.dev.example .env.dev
```

En la carpeta que ya tiene un `.env` local con la contraseña de la base persistente, consérvalo y puedes usarlo directamente; el perfil por defecto de Compose es `dev`:

```powershell
docker compose --env-file .env up --build -d
```

Para usar `.env.dev`, confirma que su contraseña PostgreSQL corresponda a la base que vas a usar. La plantilla usa el volumen de desarrollo, por lo que cambiar la contraseña de entorno no cambia automáticamente la contraseña almacenada en un volumen ya inicializado.

```powershell
docker compose --env-file .env.dev up --build -d
```

### Producción

Prepara un archivo local que no se sube a Git:

```powershell
Copy-Item .env.prod.example .env.prod
```

Cambia `POSTGRES_PASSWORD` por un secreto fuerte y único. Mientras no exista el dominio frontend, deja `CORS_ALLOWED_ORIGINS` vacío. Cuando se defina, configura el origen exacto, por ejemplo `https://app.example.com` (sin ruta y sin `*`).

```powershell
docker compose --env-file .env.prod up --build -d
```

La plantilla productiva usa un volumen diferente (`ecoair-postgres-prod-data`) para separar los datos de producción de los de desarrollo.

Los archivos `.env`, `.env.dev` y `.env.prod` son locales e ignorados por Git. No reemplaces una contraseña que ya corresponde a un volumen PostgreSQL existente sin coordinar la rotación de la contraseña en la base.

## Arquitectura

```text
Cliente --HTTP:80--> Nginx --red edge--> Backend Spring Boot
                                      Backend --red interna data--> PostgreSQL
```

Solo Nginx publica un puerto al host. El backend no publica `8080` y PostgreSQL no publica `5432`. Nginx y backend comparten la red Bridge `edge`; backend y PostgreSQL comparten la red Bridge interna `data`. El volumen de base de datos se conserva al recrear contenedores.

## Verificación y CORS

El health endpoint y la API se consultan a través de Nginx:

```powershell
docker compose ps
curl.exe -i http://localhost/actuator/health
curl.exe -i http://localhost/api/alertas
```

Preflight permitido en desarrollo (solicitud POST con las cabeceras `Content-Type` y `Authorization`):

```powershell
curl.exe -i -X OPTIONS http://localhost/api/alertas `
  -H "Origin: http://localhost:3000" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: content-type,authorization"
```

Debe responder con cabeceras `Access-Control-Allow-Origin: http://localhost:3000`, `Access-Control-Allow-Credentials: true`, los métodos permitidos y las cabeceras solicitadas.

Prueba negativa con un origen que no está en la lista:

```powershell
curl.exe -i -X OPTIONS http://localhost/api/alertas `
  -H "Origin: https://origen-no-autorizado.example" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: content-type,authorization"
```

El origen no autorizado no debe recibir `Access-Control-Allow-Origin`. En el perfil `prod` con `CORS_ALLOWED_ORIGINS` vacío, ningún origen cross-origin debe recibir permiso.

El backend aplica CORS solo a `/api/**`; se permiten `GET`, `POST` y `OPTIONS`, y las cabeceras `Content-Type` y `Authorization`.

## Operación

```powershell
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=100
docker compose --env-file .env restart
docker compose --env-file .env stop
docker compose --env-file .env down
```

`docker compose down` elimina contenedores y redes, pero conserva el volumen. No uses `docker compose down -v` salvo que quieras borrar permanentemente la base de datos. Para comprobar persistencia, consulta `/api/alertas`, detén y vuelve a iniciar Compose, y consulta la ruta otra vez.

## Ejecución local sin Docker

Requiere Java 17 y Maven. Desde `backend/`, el perfil `dev` es el predeterminado y utiliza H2 en memoria:

```powershell
cd backend
mvn spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.
