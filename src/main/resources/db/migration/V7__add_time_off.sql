-- Dated time off: leave for one staff member, or (with no staff member) the whole salon
-- closed, e.g. a public holiday. Both days are included: 24-26 December is three days.

CREATE TABLE time_off (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_id         BIGINT NOT NULL REFERENCES salon (id),
    staff_member_id  BIGINT,
    first_day        DATE   NOT NULL,
    last_day         DATE   NOT NULL,
    CONSTRAINT time_off_first_before_last CHECK (first_day <= last_day),
    -- The staff member must work at this salon. When staff_member_id is NULL (the whole
    -- salon), PostgreSQL skips the check, which is what we want.
    CONSTRAINT time_off_staff_in_same_salon
        FOREIGN KEY (staff_member_id, salon_id) REFERENCES staff_member (id, salon_id)
);

-- "Is anyone off on this date?" and "upcoming time off" both filter by salon and last day.
CREATE INDEX time_off_salon_last_day_idx ON time_off (salon_id, last_day);
