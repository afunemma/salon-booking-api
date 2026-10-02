-- Salon owners log in to manage their salon. Clients book without an account.

CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Emails are compared case-insensitively: Thabo@Example.com and thabo@example.com are the same account.
CREATE UNIQUE INDEX app_user_email_unique ON app_user (lower(email));

-- Every salon has exactly one owner. There is no production data yet, so the column
-- can be NOT NULL straight away; local test databases must be reset (see README).
ALTER TABLE salon
    ADD COLUMN owner_id BIGINT NOT NULL REFERENCES app_user (id);

CREATE INDEX salon_owner_id_idx ON salon (owner_id);
