-- Políticas de reserva: términos, ventana de aprobación y estado de pago pendiente.

ALTER TABLE common_areas ADD COLUMN terms text NOT NULL DEFAULT '';
ALTER TABLE common_areas ADD COLUMN approval_start_time time;
ALTER TABLE common_areas ADD COLUMN approval_end_time time;
ALTER TABLE common_areas ADD CONSTRAINT chk_common_area_approval_window
    CHECK (approval_start_time IS NULL OR approval_end_time IS NULL
           OR approval_start_time < approval_end_time);

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_status_check;
ALTER TABLE reservations ADD CONSTRAINT reservations_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'));
