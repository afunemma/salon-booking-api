-- Optimistic locking for bookings (ADR-0018). Every change increases the version, and an
-- update only succeeds if the version is still the one it read, so two people changing
-- the same booking at the same moment can't silently overwrite each other.

ALTER TABLE booking ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
