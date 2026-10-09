# Instrucciones para Claude (Claude Code / Proyectos)
Lee primero `docs/HANDOFF.md` y `docs/PROMPT_PROYECTO.md` (reglas de trabajo y de entrega). Los requisitos originales completos van en `docs/ESPECIFICACION_ORIGINAL.md`.
Comandos útiles: `docker compose up -d --build vecindad_nginx` · `.\scripts\smoke-test.ps1` · `cd backend; mvn verify`.
Convenciones: arquitectura hexagonal por módulo; tenant solo desde el JWT; SQL de tenant vía `TenantJdbc` con `{s}`; migraciones nunca se editan; JSON siempre con Content-Type application/json.
