-- Salons can have several staff members, each with their own working hours, and every
-- booking is with one staff member. Overlaps are now prevented per staff member instead
-- of per salon, so two stylists can both start at 10:00.

CREATE TABLE staff_member (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_id   BIGINT       NOT NULL REFERENCES salon (id),
    name       VARCHAR(255) NOT NULL,
    starts_at  TIME         NOT NULL,
    ends_at    TIME         NOT NULL,
    CONSTRAINT staff_member_starts_before_ends CHECK (starts_at < ends_at),
    -- Lets booking reference (staff member, salon) as a pair; see booking_staff_in_same_salon.
    CONSTRAINT staff_member_id_salon_unique UNIQUE (id, salon_id)
);

CREATE INDEX staff_member_salon_id_idx ON staff_member (salon_id);

-- Every existing salon gets one staff member working the salon's hours, so solo salons
-- keep working exactly as before.
INSERT INTO staff_member (salon_id, name, starts_at, ends_at)
SELECT id, 'Owner', opens_at, closes_at FROM salon;

ALTER TABLE booking ADD COLUMN staff_member_id BIGINT;

UPDATE booking b
SET staff_member_id = s.id
FROM staff_member s
WHERE s.salon_id = b.salon_id;

ALTER TABLE booking ALTER COLUMN staff_member_id SET NOT NULL;

-- A booking's staff member must work at the booking's salon. Checking the pair makes the
-- database reject a booking with another salon's staff member, whatever the Java code does.
ALTER TABLE booking
    ADD CONSTRAINT booking_staff_in_same_salon
    FOREIGN KEY (staff_member_id, salon_id) REFERENCES staff_member (id, salon_id);

-- Overlaps are now per staff member (see V2 for how the constraint works).
ALTER TABLE booking DROP CONSTRAINT booking_no_overlap;

ALTER TABLE booking
    ADD CONSTRAINT booking_no_overlap
    EXCLUDE USING gist (
        staff_member_id WITH =,
        booking_date WITH =,
        timerange(start_time, end_time) WITH &&
    )
    WHERE (status = 'BOOKED');
