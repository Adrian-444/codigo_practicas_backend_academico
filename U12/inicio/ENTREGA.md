## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 25 | Compila y ejecuta Maven. El `pom.xml` fija `maven.compiler.release=21`, así que el bytecode y la API usada son de Java 21. |
| Docker con Compose v2 | Docker Engine 24 o más reciente | PostgreSQL, la imagen de la aplicación y Testcontainers en las pruebas. |
| Git, curl y bash | cualquiera reciente | Clonar, hacer peticiones y ejecutar `evidencia.sh`. En Windows: Git Bash o WSL. |

No hace falta instalar Maven: el proyecto incluye el wrapper (`mvnw` / `mvnw.cmd`).

Comprueba las versiones:

```bash
java -version            # debe decir 25
docker compose version
```

## 2. Obtener el código en una carpeta limpia

```bash
git clone https://github.com/Adrian-444/codigo_practicas_backend_academico.git
cd codigo_practicas_backend_academico
git checkout reto-u12
cd U12/inicio
```

Todos los comandos siguientes se ejecutan desde esa carpeta.

## 3. Construcción y pruebas

Docker debe estar iniciado, porque las pruebas de integración levantan PostgreSQL con Testcontainers.

```bash
./mvnw clean test package          # Windows: .\mvnw.cmd clean test package
```

Resultado esperado: `Tests run: 23, Failures: 0, Errors: 0` y `BUILD SUCCESS`. Se genera `target/backend-academico-0.0.12-SNAPSHOT.jar`, que el `Dockerfile` copia a la imagen.

## 4. Inicio

```bash
docker compose build
docker compose up -d
docker compose ps                  # postgres (healthy) y app (Up, puerto 8080)
docker compose logs app            # debe aparecer "Started BackendAcademicoApplication"
```

Flyway crea el esquema en el primer arranque. PostgreSQL no publica su puerto: solo lo alcanza el contenedor `app` por la red interna de Compose.

### Cuentas de laboratorio

| Usuario | Contraseña | Rol | Notas |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Métricas y detalles de salud |
| `docente` | `docente123` | DOCENTE | Crea, modifica y consulta cualquier alumno |
| `a001` | `a001pass` | ALUMNO | Propietario del alumno con matrícula `A001` |
| `alumno` | `alumno123` | ALUMNO | No es propietario de ningún registro |

## 5. Peticiones y evidencia

Con los servicios arriba:

```bash
./evidencia.sh
```

El script obtiene un token CSRF, hace cada petición, compara el status obtenido con el esperado y termina con código 0 solo si todo coincide. Se puede repetir cuantas veces se quiera: usa una matrícula nueva en cada ejecución y reutiliza `A001` si ya existe.

| Observación | Petición | Esperado |
|---|---|---|
| Éxito | `POST /api/v1/alumnos` como docente con CSRF | 201 + `Location` |
| Validación | `POST` con matrícula vacía y correo inválido | 400 `DATOS_INVALIDOS` |
| Ausencia | `GET /api/v1/alumnos/999999` como docente | 404 `ALUMNO_NO_ENCONTRADO` |
| Conflicto | `POST` repitiendo la matrícula | 409 `MATRICULA_DUPLICADA` |
| Seguridad 1: autenticación | `GET /api/v1/alumnos` sin credenciales | 401 |
| Seguridad 2: CSRF | `POST` como docente sin `X-CSRF-TOKEN` | 403 |
| Seguridad 3: rol | `GET /api/v1/alumnos` (lista) como `a001` | 403 |
| Propietario | `GET /api/v1/alumnos/{id de A001}` como `a001` | 200 |
| Registro ajeno | El mismo id como `alumno` | 403 `ACCESO_DENEGADO` |
| Id inexistente para ALUMNO | `GET /api/v1/alumnos/999999` como `a001` | 403, no 404 |
| Falla de PostgreSQL | Sección 6 | readiness 503, liveness 200 |

Las mismas peticiones están en `requests.http`

Para hacer una petición a mano con token CSRF:

```bash
TOKEN=$(curl -s -c cj.txt -b cj.txt -u docente:docente123 localhost:8080/api/v1/csrf | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -i -b cj.txt -u docente:docente123 -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"matricula":"A001","nombre":"Ada Lovelace","correo":"ada@u.mx"}' localhost:8080/api/v1/alumnos
```

## 6. Falla controlada de PostgreSQL y readiness

`evidencia.sh` lo hace automáticamente en la sección 7. A mano:

```bash
docker compose stop postgres
curl -i localhost:8080/actuator/health/readiness    # 503 {"status":"DOWN"}
curl -i localhost:8080/actuator/health/liveness     # 200 {"status":"UP"}
curl -i -u docente:docente123 localhost:8080/api/v1/alumnos/999   # 503 BASE_NO_DISPONIBLE, Retry-After: 5
docker compose start postgres
curl -i localhost:8080/actuator/health/readiness    # vuelve a 200 en unos segundos
```

## 9. Cierre

```bash
docker compose down        # elimina contenedores y red; conserva el volumen datos_academicos
docker compose down -v     # además borra el volumen y todos los datos (solo para empezar de cero)
```