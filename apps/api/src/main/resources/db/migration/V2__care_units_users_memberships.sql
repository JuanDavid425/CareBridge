-- Identity: care units, user accounts and care-unit memberships.
--
-- Every account has exactly one role. Coordinators and reviewers get access to cases through
-- active memberships in care units; patients and caregivers are linked to their own case later;
-- administrators manage the instance but belong to no unit and never see case content.

CREATE TABLE care_units (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text        NOT NULL,
    name        text        NOT NULL,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT care_units_code_key UNIQUE (code),
    CONSTRAINT care_units_code_format CHECK (code ~ '^[A-Z0-9-]{3,40}$'),
    CONSTRAINT care_units_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE users (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Stable id from the synthetic seed (DEMO-USR-001); null for accounts created in the app.
    external_ref   text,
    handle         text        NOT NULL,
    email          text        NOT NULL,
    display_name   text        NOT NULL,
    role           text        NOT NULL,
    -- Delegating encoder format ({bcrypt}...). Null means the account cannot sign in.
    password_hash  text,
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_external_ref_key UNIQUE (external_ref),
    CONSTRAINT users_role_check
        CHECK (role IN ('coordinator', 'reviewer', 'patient', 'caregiver', 'admin')),
    CONSTRAINT users_handle_format CHECK (handle ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
    CONSTRAINT users_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+$'),
    -- Target of the composite foreign key below: a membership's role must be its user's role.
    CONSTRAINT users_id_role_key UNIQUE (id, role)
);

CREATE UNIQUE INDEX users_handle_lower_key ON users (lower(handle));
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

CREATE TABLE team_memberships (
    user_id       uuid        NOT NULL,
    care_unit_id  uuid        NOT NULL REFERENCES care_units (id),
    role          text        NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, care_unit_id),
    -- Only care-team roles belong to units.
    CONSTRAINT team_memberships_role_check CHECK (role IN ('coordinator', 'reviewer')),
    -- Changing a member's role requires removing the membership first.
    CONSTRAINT team_memberships_user_role_fkey
        FOREIGN KEY (user_id, role) REFERENCES users (id, role)
);

-- Case lists filter by unit: "who is an active member of unit X".
CREATE INDEX team_memberships_active_unit_idx
    ON team_memberships (care_unit_id, user_id) WHERE active;
