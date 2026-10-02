# Cartera (Fase 3a)

## Modelo
- **Cargo** (`charges`): ORDINARY, EXTRAORDINARY, INTEREST, FINE u OTHER. Pendiente = `amount + adjusted_amount - paid_amount`. Estado derivado: OPEN, PARTIAL, PAID, VOIDED.
- **Pago** (`payments`): inmutable. Si se equivocó, se **revierte** (con motivo): libera los cargos y deja una línea REVERSAL en el libro. Lo que no se alcanza a imputar queda como **saldo a favor** (`unapplied_amount`) y se aplica solo cuando aparecen cargos nuevos.
- **Imputación** (`payment_allocations`): qué parte de cada pago cubrió cada cargo.
- **Libro** (`ledger_entries`): CHARGE (+), PAYMENT (−), ADJUSTMENT_CREDIT (−), ADJUSTMENT_DEBIT (+), VOID (−), REVERSAL (+). Inmutable (trigger). El saldo del inmueble es su suma.
- **Invariante**: `saldo del libro = Σ pendiente de cargos vigentes − Σ saldo a favor`. Los tests lo verifican tras cada escenario.

## Imputación de pagos (configurable por copropiedad)
`PUT /billing/settings` define `allocationOrder` (por defecto INTEREST, EXTRAORDINARY, ORDINARY, FINE, OTHER) y `oldestFirst` (dentro de un concepto, del vencimiento más antiguo al más reciente).
Los conceptos no listados van al final. Ejemplo: deuda Ordinaria 300.000, Extraordinaria 150.000, Intereses 50.000 y pago de 300.000 → Intereses 50.000, Extraordinaria 150.000, Ordinaria 100.000.
El motor es una función pura (`PaymentAllocator`) cubierta por pruebas unitarias y aleatorias.

## Intereses de mora
Se activan en settings (`interestEnabled`, `interestMonthlyRate` en %, `graceDays`). `POST /billing/runs/interest` (por defecto a hoy) genera, por inmueble, un cargo INTEREST =
Σ saldo vencido × tasa/100/30 × días transcurridos desde `max(vencimiento + gracia, último corte)`. Cada cargo recuerda hasta qué fecha ya generó interés (`interest_accrued_until`), por lo que repetir la corrida el mismo día no duplica nada.

## Concurrencia e idempotencia
- Toda operación financiera corre en una transacción JDBC (`FinanceTx`) que primero bloquea el inmueble (`SELECT ... FOR UPDATE`): dos pagos simultáneos se serializan.
- La base de datos refuerza lo importante: `CHECK (amount + adjusted_amount >= paid_amount)`, índice único de cuota ordinaria por inmueble+periodo, índice único de `idempotency_key`.
- `POST /payments` con `Idempotency-Key`: el mismo pago se devuelve (200) si se reintenta; con otro monto/inmueble responde 409.
- La facturación masiva (`/billing/runs/ordinary`) es idempotente por inmueble+periodo y reporta creadas / ya existentes / sin coeficiente.

## Privacidad
Solo FINANCE_VIEW/FINANCE_MANAGE/PAYMENTS_CREATE (administrador, contador; consejo solo lectura) ven la cartera. El propietario ve únicamente los estados de cuenta de sus inmuebles (`/my/statement`);
arrendatario y residente **no** ven finanzas por defecto, pero la administración puede habilitarlo con `PUT /roles/{rol}/permissions/FINANCE_VIEW_OWN`. Secretaría y portería no tienen acceso.

## Auditoría
Cargos, corridas, anulaciones, notas, pagos, reversiones y cambios de configuración quedan en `audit_logs` (quién, cuándo, qué), además del libro.
