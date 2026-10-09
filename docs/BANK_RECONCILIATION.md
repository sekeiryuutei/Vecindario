# Conciliación bancaria (Fase 3b)

## Flujo
1. `POST /bank-reconciliation/imports` (multipart) valida y guarda la importación como **QUEUED** y responde **202**. El archivo (CSV o XLSX, máx. 5 MB y 5.000 filas) se procesa en segundo plano; la consulta de estado es `GET /imports/{id}`: `QUEUED → PROCESSING → COMPLETED | FAILED`.
2. Se lee con el **perfil de formato bancario** elegido, se calcula un **hash estable** de cada transacción, se puntúan los inmuebles candidatos y se decide el estado de cada fila.
3. Las filas de alta confianza se aplican solas (se registra un pago `source = BANK_RECONCILIATION` con llave idempotente). El resto queda para revisión manual con sus candidatos.
4. La administración concilia (`match`), rechaza (`reject`) o deshace (`revert`) con motivo. Todo queda en `bank_row_decisions` (inmutable) y en `audit_logs`.

## Formatos bancarios (perfiles)
Cada banco entrega columnas distintas, por eso las columnas son un **perfil configurable** (`/bank-reconciliation/profiles`): alias separados por coma para fecha, valor (o crédito/débito), descripción, referencia, pagador y id de transacción; formato de fecha (`dd/MM/yyyy`, `yyyy-MM-dd`…) y separador decimal (`,` → `1.234,56`; `.` → `1,234.56`). Las tildes y mayúsculas de los encabezados se ignoran, las filas de título previas al encabezado se saltan y el delimitador CSV (`, ; tab |`) se detecta solo.
Convención: valor **positivo = dinero que entra** (pago); negativo = salida (estado `IGNORED`). **Cualquier fila inválida hace fallar la importación** indicando la fila: nunca se descarta dinero en silencio.

## Motor de coincidencias
Por inmueble suma señales independientes (máximo 1,0):

| Señal | Peso |
|---|---|
| Código del inmueble en descripción/referencia (`T1-101`, `T1 101`, `t1101`) | 0,60 |
| Número del inmueble solo (ambiguo entre torres) | 0,15 |
| Documento del propietario/arrendatario en el texto | 0,40 |
| Nombre del pagador = persona vinculada (parcial 0,12) | 0,25 |
| Valor = deuda total / = un cargo pendiente / abono parcial | 0,25 / 0,20 / 0,05 |
| Pagador ya conciliado antes con ese inmueble (aprendizaje) | 0,30 |

Decisión (configurable en `/bank-reconciliation/settings`): **AUTO_MATCHED** solo si el mejor candidato supera `autoThreshold` (por defecto 0,90) **y** el segundo queda al menos `ambiguityGap` (0,10) por debajo; si hay dos candidatos cercanos es **AMBIGUOUS**; en cualquier otro caso **MANUAL_REVIEW**. Si `autoApply` es falso nada se aplica solo. Así, un pago de baja confianza **nunca** se aplica automáticamente.

## Idempotencia y duplicados
- El **mismo archivo** no se importa dos veces (hash SHA-256; un intento FAILED no bloquea el reintento).
- Cada transacción tiene un `tx_hash` = banco + cuenta + fecha + valor + referencia + descripción (+ id de transacción si el banco lo entrega, o el número de orden entre filas idénticas). Si ya existe, la fila queda **DUPLICATE** y no se aplica: sirve para extractos que se solapan.
- El pago se registra con la llave `bank:<tx_hash>:<n>`: reintentar o aplicar concurrentemente devuelve el mismo pago. `n` aumenta solo cuando una conciliación se revierte y se vuelve a hacer.
- `match`/`reject`/`revert` cambian el estado con un `UPDATE` condicional (solo si la fila sigue en el estado esperado): dos usuarios no pueden resolver la misma fila a la vez.

## Aprendizaje
Al conciliar manualmente se guarda el nombre normalizado del pagador → inmueble (`bank_counterparty_hints`) y aporta 0,30 en futuras coincidencias.

## Límites conocidos
Ver `docs/ROADMAP.md` (archivo solo en memoria, foto de deuda al iniciar, cola interna, solo CSV/XLSX).
