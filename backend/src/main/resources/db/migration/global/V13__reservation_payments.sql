-- Vincula una intención de pago de Wompi con una reserva de zona común.
ALTER TABLE online_payment_intents ADD COLUMN reservation_id uuid;
CREATE INDEX ix_online_intents_reservation
    ON online_payment_intents (reservation_id)
    WHERE reservation_id IS NOT NULL;
