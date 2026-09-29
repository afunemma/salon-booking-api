CREATE TABLE salon (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    opens_at   TIME         NOT NULL,
    closes_at  TIME         NOT NULL,
    CONSTRAINT salon_opens_before_closes CHECK (opens_at < closes_at)
);

CREATE TABLE service_offering (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_id          BIGINT       NOT NULL REFERENCES salon (id),
    name              VARCHAR(255) NOT NULL,
    duration_minutes  INTEGER      NOT NULL CHECK (duration_minutes > 0),
    price_from_cents  INTEGER      CHECK (price_from_cents >= 0),
    price_to_cents    INTEGER      CHECK (price_to_cents >= 0),
    CONSTRAINT service_offering_price_range CHECK (price_to_cents >= price_from_cents)
);

CREATE INDEX service_offering_salon_id_idx ON service_offering (salon_id);

CREATE TABLE booking (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_id             BIGINT       NOT NULL REFERENCES salon (id),
    service_offering_id  BIGINT       NOT NULL REFERENCES service_offering (id),
    client_name          VARCHAR(255) NOT NULL,
    client_phone         VARCHAR(32)  NOT NULL,
    booking_date         DATE         NOT NULL,
    start_time           TIME         NOT NULL,
    end_time             TIME         NOT NULL,
    status               VARCHAR(20)  NOT NULL
        CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT booking_starts_before_ends CHECK (start_time < end_time)
);

-- Finding a salon's bookings for one day is the most common query.
CREATE INDEX booking_salon_date_idx ON booking (salon_id, booking_date);
