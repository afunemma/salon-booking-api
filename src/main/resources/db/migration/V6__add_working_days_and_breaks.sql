-- Staff members' weekly days off and an optional daily break (e.g. lunch 13:00-13:30).
--
-- working_days is a bit mask: Monday = 1, Tuesday = 2, Wednesday = 4, ... Sunday = 64.
-- 127 = every day, which keeps existing staff working exactly as before.

ALTER TABLE staff_member
    ADD COLUMN working_days    SMALLINT NOT NULL DEFAULT 127,
    ADD COLUMN break_starts_at TIME,
    ADD COLUMN break_ends_at   TIME;

ALTER TABLE staff_member
    ADD CONSTRAINT staff_member_works_some_day CHECK (working_days BETWEEN 1 AND 127),
    -- A break is optional, but if there is one it has both ends and lies within working hours.
    ADD CONSTRAINT staff_member_break_within_hours CHECK (
        (break_starts_at IS NULL AND break_ends_at IS NULL)
        OR (break_starts_at IS NOT NULL AND break_ends_at IS NOT NULL
            AND starts_at <= break_starts_at AND break_starts_at < break_ends_at AND break_ends_at <= ends_at)
    );
