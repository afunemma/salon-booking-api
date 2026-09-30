-- Stop two active bookings in the same salon from overlapping in time,
-- even when they are made at exactly the same moment.
--
-- Checking "is the slot free?" in Java and then saving is not enough: two requests
-- can both pass the check before either one saves (a race condition). An exclusion
-- constraint makes PostgreSQL itself reject the second booking, atomically.

-- Lets the GiST index below use "=" on normal columns (salon_id, booking_date).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- PostgreSQL has range types for timestamps but not for times of day, so define one.
-- Ranges are half-open by default, [start, end), so back-to-back bookings
-- (10:00-10:35 and 10:35-11:10) do not count as overlapping.
CREATE TYPE timerange AS RANGE (subtype = time);

ALTER TABLE booking
    ADD CONSTRAINT booking_no_overlap
    EXCLUDE USING gist (
        salon_id WITH =,
        booking_date WITH =,
        timerange(start_time, end_time) WITH &&
    )
    WHERE (status = 'BOOKED');
