-- Política de cobro por pago en línea: el pagador asume la comisión de la
-- pasarela más la tarifa fija de la plataforma, de modo que la copropiedad
-- recibe exactamente el valor de la deuda.
ALTER TABLE public.online_payment_intents
    ADD COLUMN platform_fee_in_cents bigint NOT NULL DEFAULT 0,
    ADD COLUMN net_amount_in_cents  bigint NOT NULL DEFAULT 0;

-- Las intenciones anteriores a esta política no tenían tarifa: el valor
-- acreditado a la copropiedad era el total cobrado.
UPDATE public.online_payment_intents
   SET net_amount_in_cents = amount_in_cents
 WHERE net_amount_in_cents = 0;
