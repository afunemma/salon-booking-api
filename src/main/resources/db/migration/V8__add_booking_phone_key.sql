-- A comparable form of the client's phone number, so "082 123 4567", "0821234567" and
-- "+27 82 123 4567" count as the same client when limiting bookings per phone number.
-- The key is the last 9 digits: a South African number without its 0 or 27 prefix.

ALTER TABLE booking ADD COLUMN client_phone_key VARCHAR(20);

UPDATE booking
SET client_phone_key = right(regexp_replace(client_phone, '[^0-9]', '', 'g'), 9);

ALTER TABLE booking ALTER COLUMN client_phone_key SET NOT NULL;

-- Counting a client's upcoming bookings at a salon happens on every booking.
CREATE INDEX booking_salon_phone_key_idx ON booking (salon_id, client_phone_key);
