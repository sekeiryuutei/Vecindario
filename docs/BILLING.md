# Cartera (Fase 3a)

## Modelo
- **Cargo** (`charges`): ORDINARY, EXTRAORDINARY, INTEREST, FINE u OTHER. Pendiente = `amount + adjusted_amount - paid_amount`. Estado derivado: OPEN, PARTIAL, PAID, VOIDED.
- **Pago** (`payments`): inmutable. Si se equivocó, se **revierte** (con motivo): libera los cargos y deja una línea REVERSAL en el libro. Lo que no se alcanza a imputar queda como **saldo a favor** (`unapplied_amount`) y se aplica solo cuando aparecen cargos nuevos.
- **Imputación** (`payment_allocations`): qué parte de cada pago cubrió cada cargo.
- **Libro** (`ledger_entries`): CHARGE (+), PAYMENT (−), ADJUSTMENT_CREDIT (−), ADJUSTMENT_DEBIT (+), VOID (−), REVERSAL (+). Inmutable (trigger). El saldo del inmueble es su suma.
- **Invariante**: `saldo del libro = Σ pendiente de cargos vigentes − Σ saldo a favor`. Los tests lo verifican tras cada escenario.

## Pagos en línea: comisión de la pasarela y tarifa de la plataforma

El pago en línea no se limita a la deuda: el pagador asume **la comisión de la pasarela más una tarifa fija de la
plataforma**, de modo que la copropiedad recibe exactamente el valor de la deuda.

- `PLATFORM_FEE_AMOUNT` (por defecto `2000` COP) es la tarifa de la plataforma por transacción.
- `WOMPI_COMMISSION_RATE` (`0.0265`), `WOMPI_COMMISSION_FIXED` (`700`) y `WOMPI_COMMISSION_IVA_RATE` (`0.19`) modelan la
  comisión de Wompi Colombia (2,65% + $700 por transacción exitosa más IVA).
- El total se calcula resolviendo `total × (1 − comisión) = deuda + tarifa + parte fija`, de forma que el monto cobrado
  cubre exactamente lo que la copropiedad debe recibir.
- `GET /api/v1/my/payments/online/quote?amount=` devuelve el desglose (deuda, comisión, tarifa y total) antes de abrir el
  checkout; la respuesta de creación de la intención incluye los mismos valores.
- En `online_payment_intents` se guarda `amount_in_cents` (total cobrado), `net_amount_in_cents` (valor acreditado a la
  copropiedad) y `platform_fee_in_cents` (tarifa). El webhook valida el total y acredita en cartera **el valor neto**.
- `PLATFORM_FEE_ENABLED=false` desactiva la tarifa y la comisión: el pago en línea cobra solo la deuda.

En producción la copropiedad es el comercio de Wompi (o el propio Vecindad, con dispersión a terceros): la tarifa de la
plataforma se liquida contra el total cobrado y queda registrada por intención para su conciliación.

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
