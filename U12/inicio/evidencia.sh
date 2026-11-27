#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"

BASE="${BASE:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
COOKIES="$TMP/cookies.txt"
FALLAS=0

titulo() { printf '\n== %s ==\n' "$1"; }

comprobar() {
    local descripcion="$1" esperado="$2" obtenido="$3"
    if [[ "$obtenido" == "$esperado" ]]; then
        printf '  [OK]    %-58s esperado=%s obtenido=%s\n' "$descripcion" "$esperado" "$obtenido"
    else
        printf '  [FALLA] %-58s esperado=%s obtenido=%s\n' "$descripcion" "$esperado" "$obtenido"
        FALLAS=$((FALLAS + 1))
    fi
}

pedir() {
    curl -s -o "$TMP/cuerpo" -w '%{http_code}' "$@"
}

cuerpo() { cat "$TMP/cuerpo"; }

codigo() { grep -o '"codigo":"[A-Z_]*"' "$TMP/cuerpo" | cut -d'"' -f4; }

esperar_readiness() {
    local esperado="$1"
    for _ in $(seq 1 30); do
        [[ "$(pedir "$BASE/actuator/health/readiness")" == "$esperado" ]] && return 0
        sleep 1
    done
    return 1
}

titulo "0. Servicios"
docker compose ps
esperar_readiness 200 || { echo "La aplicación no está lista en $BASE. Ejecuta: docker compose up -d"; exit 1; }

TOKEN="$(curl -s -c "$COOKIES" -b "$COOKIES" -u docente:docente123 "$BASE/api/v1/csrf" | sed -E 's/.*"token":"([^"]+)".*/\1/')"
MATRICULA="E$(date +%s)"

crear() {
    pedir -b "$COOKIES" -u docente:docente123 -H "X-CSRF-TOKEN: $TOKEN" \
        -H 'Content-Type: application/json' -d "$1" "$BASE/api/v1/alumnos"
}

titulo "1. Éxito"
comprobar "POST /alumnos como docente con CSRF" 201 \
    "$(crear "{\"matricula\":\"$MATRICULA\",\"nombre\":\"Alumno Evidencia\",\"correo\":\"$MATRICULA@u.mx\"}")"
echo "  $(cuerpo)"
ID_NUEVO="$(sed -E 's/.*"id":([0-9]+).*/\1/' "$TMP/cuerpo")"

titulo "2. Validación 400"
comprobar "POST /alumnos con matrícula vacía y correo inválido" 400 \
    "$(crear '{"matricula":"","nombre":"A","correo":"no-es-correo"}')"
comprobar "  código" DATOS_INVALIDOS "$(codigo)"

titulo "3. Ausencia 404"
comprobar "GET /alumnos/999999 como docente" 404 \
    "$(pedir -u docente:docente123 "$BASE/api/v1/alumnos/999999")"
comprobar "  código" ALUMNO_NO_ENCONTRADO "$(codigo)"

titulo "4. Conflicto 409"
comprobar "POST /alumnos repitiendo la matrícula $MATRICULA" 409 \
    "$(crear "{\"matricula\":\"$MATRICULA\",\"nombre\":\"Otro Nombre\",\"correo\":\"otro@u.mx\"}")"
comprobar "  código" MATRICULA_DUPLICADA "$(codigo)"

titulo "5. Rechazos de seguridad con causas distintas"
comprobar "GET /alumnos sin credenciales (autenticación)" 401 \
    "$(pedir "$BASE/api/v1/alumnos")"
comprobar "POST /alumnos como docente sin token CSRF" 403 \
    "$(pedir -u docente:docente123 -H 'Content-Type: application/json' \
        -d '{"matricula":"X1","nombre":"Sin Token","correo":"x@u.mx"}' "$BASE/api/v1/alumnos")"
comprobar "GET /alumnos como alumno (rol insuficiente)" 403 \
    "$(pedir -u a001:a001pass "$BASE/api/v1/alumnos")"

titulo "6. Acceso por propietario"
crear '{"matricula":"A001","nombre":"Ada Lovelace","correo":"ada@u.mx"}' > /dev/null
pedir -u docente:docente123 "$BASE/api/v1/alumnos" > /dev/null
ID_A001="$(grep -o '"id":[0-9]*,"matricula":"A001"' "$TMP/cuerpo" | sed -E 's/"id":([0-9]+).*/\1/')"
echo "  A001 tiene id=$ID_A001; la cuenta a001 es su propietaria"
comprobar "GET /alumnos/$ID_A001 como a001 (propietario)" 200 \
    "$(pedir -u a001:a001pass "$BASE/api/v1/alumnos/$ID_A001")"
comprobar "GET /alumnos/$ID_A001 como alumno (ajeno)" 403 \
    "$(pedir -u alumno:alumno123 "$BASE/api/v1/alumnos/$ID_A001")"
comprobar "  código" ACCESO_DENEGADO "$(codigo)"
comprobar "GET /alumnos/999999 como a001 (inexistente, no se revela)" 403 \
    "$(pedir -u a001:a001pass "$BASE/api/v1/alumnos/999999")"
comprobar "GET /alumnos/$ID_A001 como docente" 200 \
    "$(pedir -u docente:docente123 "$BASE/api/v1/alumnos/$ID_A001")"

titulo "7. Falla controlada de PostgreSQL"
comprobar "readiness antes de la falla" 200 "$(pedir "$BASE/actuator/health/readiness")"
echo "  docker compose stop postgres"
docker compose stop postgres > /dev/null 2>&1
comprobar "readiness con la base detenida" 503 "$(pedir "$BASE/actuator/health/readiness")"
echo "  $(cuerpo)"
comprobar "liveness con la base detenida" 200 "$(pedir "$BASE/actuator/health/liveness")"
echo "  $(cuerpo)"
comprobar "GET /alumnos/$ID_NUEVO (no está en caché)" 503 \
    "$(pedir -u docente:docente123 "$BASE/api/v1/alumnos/$ID_NUEVO")"
comprobar "  código" BASE_NO_DISPONIBLE "$(codigo)"
comprobar "GET /alumnos/$ID_A001 como a001 (ya estaba en caché)" 200 \
    "$(pedir -u a001:a001pass "$BASE/api/v1/alumnos/$ID_A001")"
comprobar "GET /alumnos/$ID_A001 como alumno ajeno (caché no salta la regla)" 403 \
    "$(pedir -u alumno:alumno123 "$BASE/api/v1/alumnos/$ID_A001")"
echo "  docker compose start postgres"
docker compose start postgres > /dev/null 2>&1
if esperar_readiness 200; then
    comprobar "readiness después de recuperar la base" 200 200
else
    comprobar "readiness después de recuperar la base" 200 "$(pedir "$BASE/actuator/health/readiness")"
fi
comprobar "GET /alumnos/$ID_NUEVO otra vez" 200 \
    "$(pedir -u docente:docente123 "$BASE/api/v1/alumnos/$ID_NUEVO")"

titulo "Resultado"
if [[ $FALLAS -eq 0 ]]; then
    echo "  Todas las observaciones coinciden con lo esperado."
else
    echo "  $FALLAS observaciones no coinciden."
fi
exit "$FALLAS"
