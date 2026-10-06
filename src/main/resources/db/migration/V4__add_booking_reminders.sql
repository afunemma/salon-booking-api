-- One row per booking that the reminder job has tried to remind.
--
-- The UNIQUE booking_id makes "at most one reminder per booking" a database rule, not just
-- something the Java code is careful about. A failed attempt keeps its row (status FAILED)
-- and is retried on the job's next run.

CREATE TABLE booking_reminder (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id       BIGINT      NOT NULL UNIQUE REFERENCES booking (id),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    attempts         INTEGER     NOT NULL CHECK (attempts > 0),
    last_attempt_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT booking_reminder_sent_has_time CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);
