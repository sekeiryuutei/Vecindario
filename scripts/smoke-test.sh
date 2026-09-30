#!/usr/bin/env bash
# Prueba rápida contra el sistema levantado (requiere APP_SEED_ENABLED=true). Usa solo curl.
set -uo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
BASE="http://localhost:${NGINX_PORT:-48124}/api/v1"
PASS="${APP_SEED_PASSWORD:-Demo#2026!}"
fail=0
ok()   { echo "  OK   $1"; }
bad()  { echo "  FAIL $1"; fail=1; }
tok()  { sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "1) health";  [ "$(code "http://localhost:${NGINX_PORT:-48124}/actuator/health")" = 200 ] && ok "health 200" || bad "health"

echo "2) login admin (2 copropiedades) + selección"
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"admin@vecindad.local\",\"password\":\"$PASS\"}")
T0=$(echo "$LOGIN" | tok); [ -n "$T0" ] && ok "login" || bad "login: $LOGIN"
NORTE=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $T0" | sed 's/},{/}\n{/g' | grep 'demo_norte' | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
SUR=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $T0" | sed 's/},{/}\n{/g' | grep 'demo_sur' | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
[ -n "$NORTE" ] && [ -n "$SUR" ] && ok "ids de copropiedades" || bad "ids de copropiedades"
TN=$(curl -s -X POST "$BASE/auth/select-tenant" -H "Authorization: Bearer $T0" -H 'Content-Type: application/json' -d "{\"tenantId\":\"$NORTE\"}" | tok)
[ -n "$TN" ] && ok "select-tenant norte" || bad "select-tenant"

echo "3) inmuebles de Norte"
[ "$(code "$BASE/properties?size=5" -H "Authorization: Bearer $TN")" = 200 ] && ok "GET /properties 200" || bad "GET /properties"

echo "4) aislamiento: admin.sur NO puede entrar a Norte"
TS=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"admin.sur@demo-sur.local\",\"password\":\"$PASS\"}" | tok)
R=$(code -X POST "$BASE/auth/select-tenant" -H "Authorization: Bearer $TS" -H 'Content-Type: application/json' -d "{\"tenantId\":\"$NORTE\"}")
[ "$R" = 403 ] && ok "select-tenant ajeno => 403" || bad "aislamiento (obtuvo $R)"

echo "5) sin token => 401"
[ "$(code "$BASE/properties")" = 401 ] && ok "401" || bad "401"

[ $fail = 0 ] && echo "TODO OK" || { echo "HAY FALLOS"; exit 1; }
