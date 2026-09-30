#!/usr/bin/env bash
# Restaura un respaldo creado con backup.sh. DESTRUYE la base actual.
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || { echo "Uso: $0 backups/vecindad_YYYYmmdd_HHMMSS.sql.gz"; exit 1; }
FILE="$1"; [ -f "$FILE" ] || { echo "No existe $FILE"; exit 1; }
set -a; . ./.env; set +a
read -r -p "Esto BORRA la base '$DATABASE_NAME' y la reemplaza con $FILE. Escribe SI para continuar: " ok
[ "$ok" = "SI" ] || { echo "Cancelado."; exit 1; }
docker compose stop vecindad_backend
docker exec vecindad_postgresql psql -U "$DATABASE_USERNAME" -d postgres -c "DROP DATABASE IF EXISTS \"$DATABASE_NAME\" WITH (FORCE);"
docker exec vecindad_postgresql psql -U "$DATABASE_USERNAME" -d postgres -c "CREATE DATABASE \"$DATABASE_NAME\";"
gunzip -c "$FILE" | docker exec -i vecindad_postgresql psql -v ON_ERROR_STOP=1 -U "$DATABASE_USERNAME" -d "$DATABASE_NAME" > /dev/null
docker compose start vecindad_backend
echo "Restauración completada."
