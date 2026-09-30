#!/usr/bin/env bash
# Respaldo lógico de TODA la base (schema público + un schema por copropiedad).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
mkdir -p backups
FILE="backups/vecindad_$(date +%Y%m%d_%H%M%S).sql.gz"
docker exec vecindad_postgresql pg_dump -U "$DATABASE_USERNAME" -d "$DATABASE_NAME" --no-owner | gzip > "$FILE"
echo "Respaldo creado: $FILE ($(du -h "$FILE" | cut -f1))"
echo "Copia este archivo fuera de esta máquina: el script NO sube respaldos a ningún servicio en la nube."
